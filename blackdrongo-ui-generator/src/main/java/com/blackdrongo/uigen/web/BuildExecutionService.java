package com.blackdrongo.uigen.web;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class BuildExecutionService {
    private static final DateTimeFormatter DB_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SCHEDULE_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int BUILD_QUEUE_LIMIT = 50;
    private static final int MAX_PARALLEL_SCENARIOS = 4;
    private static final int MAX_RUN_MESSAGE_LENGTH = 500;
    private final TemplateGenerator generator = new TemplateGenerator();
    private final ExecutorService buildExecutor = new ThreadPoolExecutor(
            2,
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(BUILD_QUEUE_LIMIT),
            namedFactory("build-exec-"),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static ThreadFactory namedFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PostConstruct
    void startScheduler() {
        scheduler.scheduleWithFixedDelay(this::processDueSchedules, 10, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopScheduler() {
        scheduler.shutdownNow();
        buildExecutor.shutdownNow();
    }

    public SubmitResult submit(BuildLaunchRequest request) {
        BuildPlan plan = resolvePlan(request);
        if (!"NOW".equals(plan.scheduleRecurrence())) {
            BuildStore.BuildScheduleEntry schedule = BuildStore.createSchedule(
                    request,
                    plan.project().project().getProjectName(),
                    plan.targetSummary(),
                    plan.scheduleRecurrence(),
                    plan.nextRunAt(),
                    ZoneId.systemDefault().getId()
            );
            return new SubmitResult(true, "Build scheduled for " + schedule.nextRunAt() + ".", null, schedule.id());
        }

        BuildStore.BuildRunEntry run = BuildStore.createRun(
                plan.project().id(),
                plan.project().project().getProjectName(),
                plan.buildName(),
                plan.selectionType(),
                plan.targetSummary(),
                request.getTags(),
                request.isParallel(),
                request.getHeadlessMode(),
                request.getMvnArgs(),
                "MANUAL",
                null
        );
        boolean accepted = launchRunAsync(run, plan);
        if (!accepted) {
            return new SubmitResult(false, "Build queue is full. Try again later.", run.id(), null);
        }
        return new SubmitResult(false, "Build queued for execution.", run.id(), null);
    }

    public void cancelSchedule(String scheduleId) {
        BuildStore.cancelSchedule(scheduleId);
    }

    public List<BuildStore.BuildRunEntry> recentRuns() {
        return BuildStore.listRuns(100);
    }

    public List<BuildStore.BuildScheduleEntry> schedules() {
        return BuildStore.listSchedules();
    }

    private void processDueSchedules() {
        String now = BuildStore.nowStamp();
        for (BuildStore.BuildScheduleEntry schedule : BuildStore.dueSchedules(now)) {
            try {
                BuildLaunchRequest request = toRequest(schedule);
                BuildPlan plan = resolvePlan(request);
                BuildStore.BuildRunEntry run = BuildStore.createRun(
                        schedule.projectId(),
                        schedule.projectName(),
                        schedule.buildName(),
                        schedule.selectionType(),
                        schedule.targetSummary(),
                        schedule.tags(),
                        schedule.parallel(),
                        schedule.headlessMode(),
                        schedule.mvnArgs(),
                        "SCHEDULED",
                        schedule.id()
                );
                boolean accepted = launchRunAsync(run, plan);
                if (!accepted) {
                    BuildStore.updateRunState(run.id(), "REJECTED", "Build queue is full. Try again later.", null, BuildStore.nowStamp());
                    continue;
                }
                String lastRun = BuildStore.nowStamp();
                String nextRun = nextRunStamp(schedule.recurrence(), schedule.nextRunAt());
                BuildStore.updateScheduleAfterRun(schedule.id(), lastRun, nextRun, nextRun == null ? "COMPLETED" : "ACTIVE");
            } catch (Exception e) {
                BuildStore.updateScheduleAfterRun(schedule.id(), BuildStore.nowStamp(), null, "FAILED");
            }
        }
    }

    private boolean launchRunAsync(BuildStore.BuildRunEntry run, BuildPlan plan) {
        try {
            buildExecutor.submit(() -> executeRun(run, plan));
            return true;
        } catch (RejectedExecutionException e) {
            String now = BuildStore.nowStamp();
            BuildStore.updateRunState(run.id(), "REJECTED", "Build queue is full. Try again later.", now, now);
            return false;
        }
    }

    private void executeRun(BuildStore.BuildRunEntry run, BuildPlan plan) {
        String startedAt = BuildStore.nowStamp();
        BuildStore.updateRunState(run.id(), "RUNNING", "Running " + plan.scenarios().size() + " scenario(s).", startedAt, null);
        if (plan.scenarios().isEmpty()) {
            BuildStore.updateRunState(run.id(), "SKIPPED", "No scenarios matched this build.", startedAt, BuildStore.nowStamp());
            return;
        }

        List<ScenarioOutcome> outcomes = runScenarios(run, plan);
        long success = outcomes.stream().filter(item -> "SUCCESS".equals(item.state())).count();
        long failure = outcomes.stream().filter(item -> "FAILURE".equals(item.state())).count();
        String finalState = failure > 0 ? "FAILURE" : (success > 0 ? "SUCCESS" : "SKIPPED");
        String message = summarizeRunOutcomes(success, failure, outcomes);
        BuildStore.updateRunState(run.id(), finalState, message, startedAt, BuildStore.nowStamp());
    }

    private List<ScenarioOutcome> runScenarios(BuildStore.BuildRunEntry run, BuildPlan plan) {
        List<ScenarioOutcome> outcomes = new ArrayList<>();
        if (!plan.parallel() || plan.scenarios().size() == 1) {
            for (ScenarioStore.ScenarioEntry scenario : plan.scenarios()) {
                outcomes.add(executeScenario(run, plan.project(), scenario, plan.mvnArgs(), plan.headlessMode()));
            }
            return outcomes;
        }

        ExecutorService parallelExecutor = Executors.newFixedThreadPool(
                Math.min(MAX_PARALLEL_SCENARIOS, plan.scenarios().size()),
                namedFactory("scenario-parallel-")
        );
        try {
            List<Future<ScenarioOutcome>> futures = new ArrayList<>();
            for (ScenarioStore.ScenarioEntry scenario : plan.scenarios()) {
                futures.add(parallelExecutor.submit(() ->
                        executeScenario(run, plan.project(), scenario, plan.mvnArgs(), plan.headlessMode())));
            }
            for (Future<ScenarioOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (Exception e) {
                    outcomes.add(new ScenarioOutcome("", "Unknown scenario", "FAILURE",
                            e.getMessage() == null ? "Parallel execution failed." : e.getMessage()));
                }
            }
            return outcomes;
        } finally {
            parallelExecutor.shutdown();
        }
    }

    private ScenarioOutcome executeScenario(BuildStore.BuildRunEntry run,
                                            ProjectStore.ProjectEntry project,
                                            ScenarioStore.ScenarioEntry scenario,
                                            String mvnArgsOverride,
                                            String headlessMode) {
        Path executionRoot = null;
        try {
            ScenarioStore.updateRunStatus(scenario.id(), "RUNNING", "Running build...");
            FeatureStore.FeatureEntry feature = FeatureStore.findById(scenario.featureId())
                    .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + scenario.featureId()));
            GenerateRequest request = buildGenerateRequest(project, feature, scenario, mvnArgsOverride, headlessMode);
            request.setProjectName(executionWorkspaceName(run, project, scenario));
            TemplateGenerator.GenerationResult result = generator.generate(request);
            executionRoot = result.getProjectRoot();
            Path featureFile = result.getProjectRoot()
                    .resolve("src/test/resources/features")
                    .resolve(result.getFeatureFileName());
            TestRunResult runResult = TestRunner.run(result.getProjectRoot(),
                    request.getScenarioTags(), request.getMvnArgs(), request.getScenarioName(), featureFile);
            String state = runResult.exitCode() == 0 ? "SUCCESS" : "FAILURE";
            String safeMessage = TestRunner.safeMessage(runResult.summary());
            ScenarioStore.updateRunStatus(scenario.id(), state, safeMessage);
            return new ScenarioOutcome(scenario.id(), scenario.request().getScenarioName(), state, safeMessage);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Build execution failed." : e.getMessage();
            ScenarioStore.updateRunStatus(scenario.id(), "FAILURE", message);
            return new ScenarioOutcome(scenario.id(), scenario.request().getScenarioName(), "FAILURE", message);
        } finally {
            cleanupExecutionWorkspace(executionRoot);
        }
    }

    private String executionWorkspaceName(BuildStore.BuildRunEntry run,
                                          ProjectStore.ProjectEntry project,
                                          ScenarioStore.ScenarioEntry scenario) {
        String base = TemplateGenerator.sanitizeProjectName(project.project().getProjectName());
        String runId = shortenId(run.id());
        String scenarioId = shortenId(scenario.id());
        return base + "-run-" + runId + "-scenario-" + scenarioId;
    }

    private String shortenId(String value) {
        String normalized = normalize(value);
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 8);
    }

    private void cleanupExecutionWorkspace(Path executionRoot) {
        if (executionRoot == null) {
            return;
        }
        try {
            RuntimeCleaner.deleteRecursively(executionRoot);
        } catch (IOException ignored) {
            // best effort cleanup for temporary execution workspaces
        }
    }

    private String summarizeRunOutcomes(long success, long failure, List<ScenarioOutcome> outcomes) {
        String summary = "Pass " + success + " / Fail " + failure + " / Total " + outcomes.size();
        List<String> failed = new ArrayList<>();
        for (ScenarioOutcome outcome : outcomes) {
            if (!"FAILURE".equals(outcome.state())) {
                continue;
            }
            String name = normalize(outcome.scenarioName());
            String message = normalize(outcome.message());
            String detail = name.isBlank() ? "Failure" : name;
            if (!message.isBlank()) {
                detail += ": " + message;
            }
            failed.add(detail);
        }
        if (failed.isEmpty()) {
            return summary;
        }
        String joined = String.join("; ", failed);
        String full = summary + " | Failed: " + joined;
        if (full.length() <= MAX_RUN_MESSAGE_LENGTH) {
            return full;
        }
        return full.substring(0, MAX_RUN_MESSAGE_LENGTH - 3) + "...";
    }

    private BuildPlan resolvePlan(BuildLaunchRequest request) {
        String projectId = normalize(request.getProjectId());
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("Project is required.");
        }
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project is required."));
        String buildName = normalize(request.getBuildName());
        if (buildName.isBlank()) {
            throw new IllegalArgumentException("Build name is required.");
        }
        String selectionType = normalizeSelection(request.getTargetType());
        validateSelectionInputs(request, selectionType);
        List<ScenarioStore.ScenarioEntry> scenarios = switch (selectionType) {
            case "FEATURES" -> collectFeatureScenarios(projectId, request.getFeatureIds());
            case "SCENARIOS" -> collectSelectedScenarios(projectId, request.getScenarioIds());
            case "TAGS" -> collectTaggedScenarios(projectId, request.getTags());
            default -> ScenarioStore.listByProject(projectId);
        };
        scenarios.sort(Comparator.comparing(item -> item.request().getScenarioName(), String.CASE_INSENSITIVE_ORDER));
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException(emptyScenarioMessage(selectionType));
        }
        String targetSummary = buildTargetSummary(selectionType, scenarios, request);
        String recurrence = normalizeScheduleMode(request.getScheduleMode());
        String nextRunAt = "NOW".equals(recurrence) ? "" : parseScheduledAt(request.getScheduledAt());
        if (!"NOW".equals(recurrence) && nextRunAt.isBlank()) {
            throw new IllegalArgumentException("Schedule time is required.");
        }
        return new BuildPlan(project, scenarios, buildName, selectionType, targetSummary, recurrence, nextRunAt,
                request.isParallel(), normalize(request.getMvnArgs()), normalizeHeadlessMode(request.getHeadlessMode()));
    }

    private List<ScenarioStore.ScenarioEntry> collectFeatureScenarios(String projectId, List<String> featureIds) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ScenarioStore.ScenarioEntry> scenarios = new ArrayList<>();
        for (String featureId : featureIds) {
            FeatureStore.FeatureEntry feature = FeatureStore.findById(normalize(featureId)).orElse(null);
            if (feature == null || !projectId.equals(feature.projectId())) {
                continue;
            }
            for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.listByFeature(feature.id())) {
                if (seen.add(scenario.id())) {
                    scenarios.add(scenario);
                }
            }
        }
        return scenarios;
    }

    private List<ScenarioStore.ScenarioEntry> collectSelectedScenarios(String projectId, List<String> scenarioIds) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ScenarioStore.ScenarioEntry> scenarios = new ArrayList<>();
        for (String scenarioId : scenarioIds) {
            ScenarioStore.ScenarioEntry scenario = ScenarioStore.findById(normalize(scenarioId)).orElse(null);
            if (scenario == null || !projectId.equals(scenario.projectId()) || !seen.add(scenario.id())) {
                continue;
            }
            scenarios.add(scenario);
        }
        return scenarios;
    }

    private List<ScenarioStore.ScenarioEntry> collectTaggedScenarios(String projectId, String tags) {
        Set<String> tagSet = normalizeTags(tags);
        List<ScenarioStore.ScenarioEntry> scenarios = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.listByProject(projectId)) {
            Set<String> scenarioTags = normalizeTags(scenario.request().getScenarioTags());
            boolean match = scenarioTags.stream().anyMatch(tagSet::contains);
            if (match) {
                scenarios.add(scenario);
            }
        }
        return scenarios;
    }

    private Set<String> normalizeTags(String raw) {
        Set<String> tags = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return tags;
        }
        String[] parts = raw.replace(",", " ").trim().split("\\s+");
        for (String part : parts) {
            String value = normalize(part);
            if (!value.isBlank()) {
                tags.add(value.startsWith("@") ? value.toLowerCase(Locale.ROOT) : "@" + value.toLowerCase(Locale.ROOT));
            }
        }
        return tags;
    }

    private String buildTargetSummary(String selectionType, List<ScenarioStore.ScenarioEntry> scenarios, BuildLaunchRequest request) {
        return switch (selectionType) {
            case "FEATURES" ->
                    Math.max(0, request.getFeatureIds().size()) + " feature(s) / " + scenarios.size() + " scenario(s)";
            case "SCENARIOS" -> scenarios.size() + " selected scenario(s)";
            case "TAGS" -> "Tags: " + normalize(request.getTags()) + " / " + scenarios.size() + " scenario(s)";
            default -> "Project-wide / " + scenarios.size() + " scenario(s)";
        };
    }

    private GenerateRequest buildGenerateRequest(ProjectStore.ProjectEntry project,
                                                 FeatureStore.FeatureEntry feature,
                                                 ScenarioStore.ScenarioEntry scenario,
                                                 String mvnArgsOverride,
                                                 String headlessMode) {
        ProjectRequest source = project.project().copy();
        if ("HEADED".equals(headlessMode)) {
            source.setHeadless(false);
        } else if ("HEADLESS".equals(headlessMode)) {
            source.setHeadless(true);
        }
        GenerateRequest request = new GenerateRequest();
        request.setProjectName(source.getProjectName());
        request.setEngine(source.getEngine());
        request.setBrowser(source.getBrowser());
        request.setHeadless(source.isHeadless());
        request.setChromeStartMaximized(source.isChromeStartMaximized());
        request.setChromeIncognito(source.isChromeIncognito());
        request.setChromeDisableNotifications(source.isChromeDisableNotifications());
        request.setChromeDisablePopupBlocking(source.isChromeDisablePopupBlocking());
        request.setChromeAcceptInsecureCerts(source.isChromeAcceptInsecureCerts());
        request.setChromeCustomArgs(source.getChromeCustomArgs());
        request.setFirefoxPrivateMode(source.isFirefoxPrivateMode());
        request.setFirefoxAcceptInsecureCerts(source.isFirefoxAcceptInsecureCerts());
        request.setFirefoxCustomArgs(source.getFirefoxCustomArgs());
        request.setEdgeStartMaximized(source.isEdgeStartMaximized());
        request.setEdgeInPrivate(source.isEdgeInPrivate());
        request.setEdgeAcceptInsecureCerts(source.isEdgeAcceptInsecureCerts());
        request.setEdgeCustomArgs(source.getEdgeCustomArgs());
        String scenarioUrl = scenario.request().getBaseUrl();
        String baseUrl = scenarioUrl == null || scenarioUrl.isBlank() ? feature.request().getBaseUrl() : scenarioUrl;
        request.setBaseUrl(baseUrl == null || baseUrl.isBlank() ? source.getBaseUrl() : baseUrl);
        request.setFeatureName(feature.request().getFeatureName());
        request.setFeatureId(feature.id());
        request.setScenarioName(scenario.request().getScenarioName());
        request.setScenarioSteps(scenario.request().getScenarioSteps());
        request.setScenarioTags(scenario.request().getScenarioTags());
        request.setMvnArgs(mvnArgsOverride == null || mvnArgsOverride.isBlank() ? scenario.request().getMvnArgs() : mvnArgsOverride);
        request.setProjectMode(true);
        List<ScenarioRequest> featureScenarios = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry item : ScenarioStore.listByFeature(feature.id())) {
            featureScenarios.add(item.request().copy());
        }
        request.setFeatureScenarios(featureScenarios);
        return request;
    }

    private BuildLaunchRequest toRequest(BuildStore.BuildScheduleEntry schedule) {
        BuildLaunchRequest request = new BuildLaunchRequest();
        request.setBuildName(schedule.buildName());
        request.setProjectId(schedule.projectId());
        request.setTargetType(schedule.selectionType());
        request.setFeatureIds(schedule.featureIds());
        request.setScenarioIds(schedule.scenarioIds());
        request.setTags(schedule.tags());
        request.setParallel(schedule.parallel());
        request.setHeadlessMode(schedule.headlessMode());
        request.setMvnArgs(schedule.mvnArgs());
        request.setScheduleMode(schedule.recurrence());
        request.setScheduledAt(schedule.nextRunAt());
        return request;
    }

    private String normalizeSelection(String value) {
        String selection = normalize(value).toUpperCase(Locale.ROOT);
        if (selection.equals("FEATURES") || selection.equals("SCENARIOS") || selection.equals("TAGS")) {
            return selection;
        }
        return "PROJECT";
    }

    private String normalizeScheduleMode(String value) {
        String mode = normalize(value).toUpperCase(Locale.ROOT);
        if (mode.equals("ONCE") || mode.equals("DAILY") || mode.equals("WEEKLY")) {
            return mode;
        }
        return "NOW";
    }

    private String normalizeHeadlessMode(String value) {
        String mode = normalize(value).toUpperCase(Locale.ROOT);
        if (mode.equals("HEADED") || mode.equals("HEADLESS")) {
            return mode;
        }
        return "DEFAULT";
    }

    private void validateSelectionInputs(BuildLaunchRequest request, String selectionType) {
        if ("FEATURES".equals(selectionType) && request.getFeatureIds().isEmpty()) {
            throw new IllegalArgumentException("Select at least one feature.");
        }
        if ("SCENARIOS".equals(selectionType) && request.getScenarioIds().isEmpty()) {
            throw new IllegalArgumentException("Select at least one scenario.");
        }
        if ("TAGS".equals(selectionType) && normalize(request.getTags()).isBlank()) {
            throw new IllegalArgumentException("Tag filter is required.");
        }
    }

    private String parseScheduledAt(String value) {
        String raw = normalize(value);
        if (raw.isBlank()) {
            return "";
        }
        try {
            LocalDateTime scheduledAt = LocalDateTime.parse(raw, SCHEDULE_INPUT);
            if (!scheduledAt.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Schedule time must be in the future.");
            }
            return scheduledAt.format(DB_STAMP);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Schedule time is invalid.");
        }
    }

    private String emptyScenarioMessage(String selectionType) {
        return switch (selectionType) {
            case "FEATURES" -> "No scenarios found for the selected features.";
            case "SCENARIOS" -> "Selected scenarios are no longer available.";
            case "TAGS" -> "No scenarios matched the tag filter.";
            default -> "The selected project has no scenarios to run.";
        };
    }

    private String nextRunStamp(String recurrence, String currentRunAt) {
        if ("DAILY".equalsIgnoreCase(recurrence)) {
            return LocalDateTime.parse(currentRunAt, DB_STAMP).plusDays(1).format(DB_STAMP);
        }
        if ("WEEKLY".equalsIgnoreCase(recurrence)) {
            return LocalDateTime.parse(currentRunAt, DB_STAMP).plusWeeks(1).format(DB_STAMP);
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record SubmitResult(boolean scheduled, String message, String runId, String scheduleId) {
    }

    private record BuildPlan(ProjectStore.ProjectEntry project,
                             List<ScenarioStore.ScenarioEntry> scenarios,
                             String buildName,
                             String selectionType,
                             String targetSummary,
                             String scheduleRecurrence,
                             String nextRunAt,
                             boolean parallel,
                             String mvnArgs,
                             String headlessMode) {
    }

    private record ScenarioOutcome(String scenarioId, String scenarioName, String state, String message) {
    }
}
