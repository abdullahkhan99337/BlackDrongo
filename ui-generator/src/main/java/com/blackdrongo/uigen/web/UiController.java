package com.blackdrongo.uigen.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

@Controller
public class UiController {

    private final TemplateGenerator generator = new TemplateGenerator();
    private static final Set<String> BLOCKED_CHROME_ARG_PREFIXES = Set.of(
            "--user-data-dir",
            "--profile-directory",
            "--remote-debugging-port"
    );

    @GetMapping("/")
    public String home(Model model) {
        reconcileRunStatuses();
        List<ProjectDashboardRow> projectCards = projectDashboardRows();
        model.addAttribute("projectCount", projectCards.size());
        model.addAttribute("projectCards", projectCards);
        return "home";
    }

    @GetMapping("/projects")
    public String projects(Model model) {
        reconcileRunStatuses();
        List<ProjectDashboardRow> projectCards = projectDashboardRows();
        model.addAttribute("projectCount", projectCards.size());
        model.addAttribute("projectCards", projectCards);
        return "projects";
    }

    @GetMapping("/features")
    public String featuresOverview(Model model) {
        reconcileRunStatuses();
        List<FeatureOverviewRow> rows = featureOverviewRows();
        model.addAttribute("featureRows", rows);
        return "features-overview";
    }

    @GetMapping("/scenarios")
    public String scenariosOverview(Model model) {
        reconcileRunStatuses();
        List<ScenarioOverviewRow> rows = scenarioOverviewRows();
        model.addAttribute("scenarioRows", rows);
        return "scenarios-overview";
    }

    @GetMapping("/running-tests")
    public String runningTests(Model model) {
        reconcileRunStatuses();
        List<RunningTestRow> rows = runningTestRows();
        long running = rows.stream().filter(r -> "RUNNING".equals(r.state())).count();
        long success = rows.stream().filter(r -> "SUCCESS".equals(r.state())).count();
        long failure = rows.stream().filter(r -> "FAILURE".equals(r.state())).count();
        long notRun = rows.stream().filter(r -> "NOT_RUN".equals(r.state())).count();
        model.addAttribute("runningRows", rows);
        model.addAttribute("runningCount", running);
        model.addAttribute("successCount", success);
        model.addAttribute("failureCount", failure);
        model.addAttribute("notRunCount", notRun);
        return "running-tests";
    }

    @GetMapping("/running-tests/statuses")
    public ResponseEntity<List<RunningTestRow>> runningTestStatuses() {
        reconcileRunStatuses();
        return ResponseEntity.ok(runningTestRows());
    }

    @GetMapping("/projects/new")
    public String newProject(Model model) {
        ProjectRequest project = new ProjectRequest();
        project.setProjectName("automation-project");
        project.setEngine("selenium");
        project.setBrowser("chrome");
        project.setHeadless(false);
        project.setBaseUrl("");
        project.setChromeStartMaximized(true);
        project.setChromeIncognito(true);
        project.setChromeDisableNotifications(true);
        project.setChromeDisablePopupBlocking(true);
        project.setChromeAcceptInsecureCerts(true);
        project.setChromeCustomArgs("");
        project.setFirefoxPrivateMode(true);
        project.setFirefoxAcceptInsecureCerts(true);
        project.setFirefoxCustomArgs("");
        project.setEdgeStartMaximized(true);
        project.setEdgeInPrivate(true);
        project.setEdgeAcceptInsecureCerts(true);
        project.setEdgeCustomArgs("");
        model.addAttribute("project", project);
        model.addAttribute("editMode", false);
        model.addAttribute("projectId", "");
        model.addAttribute("formAction", "/projects");
        return "project";
    }

    @GetMapping("/projects/{projectId}/edit")
    public String editProject(@PathVariable("projectId") String projectId, Model model) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        model.addAttribute("project", project.project());
        model.addAttribute("editMode", true);
        model.addAttribute("projectId", projectId);
        model.addAttribute("formAction", "/projects/" + projectId + "/update");
        return "project";
    }

    @PostMapping("/projects")
    public String createProject(@ModelAttribute("project") ProjectRequest project) {
        normalizeChromeOptions(project);
        ProjectStore.save(project);
        return "redirect:/projects";
    }

    @PostMapping("/projects/{projectId}/update")
    public String updateProject(@PathVariable("projectId") String projectId,
                                @ModelAttribute("project") ProjectRequest project) {
        ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        normalizeChromeOptions(project);
        ProjectStore.update(projectId, project);
        return "redirect:/projects";
    }

    @PostMapping("/projects/delete/{projectId}")
    public String deleteProject(@PathVariable("projectId") String projectId) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        ProjectStore.delete(projectId);
        deleteProjectDir(project.project().getProjectName());
        return "redirect:/projects";
    }

    @PostMapping("/projects/{projectId}/edit-inline")
    public String editProjectInline(@PathVariable("projectId") String projectId,
                                    @RequestParam("projectName") String projectName,
                                    @RequestParam("engine") String engine,
                                    @RequestParam("browser") String browser,
                                    @RequestParam("headless") boolean headless,
                                    @RequestParam(value = "baseUrl", required = false) String baseUrl) {
        ProjectStore.ProjectEntry existing = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        ProjectRequest updated = existing.project().copy();
        updated.setProjectName(projectName);
        updated.setEngine(engine);
        updated.setBrowser(browser);
        updated.setHeadless(headless);
        updated.setBaseUrl(baseUrl == null ? "" : baseUrl);
        normalizeChromeOptions(updated);
        ProjectStore.update(projectId, updated);
        return "redirect:/projects";
    }

    @GetMapping("/projects/{projectId}/features")
    public String features(@PathVariable("projectId") String projectId, Model model) {
        reconcileRunStatuses();
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureRequest feature = new FeatureRequest();
        feature.setFeatureName("Main Feature");
        feature.setFeatureDescription("");
        feature.setBaseUrl("");
        feature.setJiraUserStories("");
        model.addAttribute("project", project);
        model.addAttribute("projectCards", projectDashboardRows());
        model.addAttribute("feature", feature);
        model.addAttribute("featureCards", featureCards(projectId));
        ScenarioRequest scenario = new ScenarioRequest();
        scenario.setScenarioName("Open the home page");
        scenario.setScenarioSteps("Given I open the application");
        scenario.setScenarioTags("@smoke");
        scenario.setMvnArgs("");
        model.addAttribute("scenario", scenario);
        return "features";
    }

    @GetMapping("/projects/{projectId}")
    public String projectDetails(@PathVariable("projectId") String projectId,
                                 @RequestParam(value = "state", required = false) String state,
                                 Model model) {
        reconcileRunStatuses();
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        List<FeatureStore.FeatureEntry> features = FeatureStore.listByProject(projectId);
        String stateFilter = state == null ? "" : state.trim().toUpperCase();
        List<ProjectExecutionRow> executionRows = projectExecutionRows(projectId, stateFilter);
        FeatureRequest feature = new FeatureRequest();
        feature.setFeatureName("Main Feature");
        feature.setFeatureDescription("");
        feature.setBaseUrl("");
        feature.setJiraUserStories("");
        ScenarioRequest scenario = new ScenarioRequest();
        scenario.setScenarioName("Open the home page");
        scenario.setScenarioSteps("Given I open the application");
        scenario.setScenarioTags("@smoke");
        scenario.setMvnArgs("");

        model.addAttribute("project", project);
        model.addAttribute("projectCards", projectDashboardRows());
        model.addAttribute("featureCards", featureCards(projectId));
        model.addAttribute("featureRows", features);
        model.addAttribute("feature", feature);
        model.addAttribute("scenario", scenario);
        model.addAttribute("executionRows", executionRows);
        model.addAttribute("stateFilter", stateFilter);
        return "project-details";
    }

    @GetMapping("/projects/{projectId}/scenarios")
    public String projectScenarios(@PathVariable("projectId") String projectId) {
        ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        return "redirect:/projects/" + projectId + "/features";
    }

    @GetMapping("/projects/{projectId}/executions")
    public String projectExecutions(@PathVariable("projectId") String projectId,
                                    @RequestParam(value = "state", required = false) String state,
                                    Model model) {
        reconcileRunStatuses();
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        String stateFilter = state == null ? "" : state.trim().toUpperCase();
        model.addAttribute("project", project);
        model.addAttribute("projectCards", projectDashboardRows());
        model.addAttribute("stateFilter", stateFilter);
        model.addAttribute("executionRows", projectExecutionRows(projectId, stateFilter));
        return "project-executions";
    }

    @PostMapping("/projects/{projectId}/features/save")
    public String saveFeature(@PathVariable("projectId") String projectId,
                              @RequestParam(value = "sourcePage", required = false) String sourcePage,
                              @ModelAttribute("feature") FeatureRequest feature) {
        FeatureStore.FeatureEntry saved = FeatureStore.save(projectId, feature);
        return "redirect:" + featureReturnUrl(projectId, saved.id(), sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/update")
    public String updateFeature(@PathVariable("projectId") String projectId,
                                @RequestParam(value = "sourcePage", required = false) String sourcePage,
                                @ModelAttribute("feature") FeatureRequest feature) {
        if (feature.getFeatureId() == null || feature.getFeatureId().isBlank()) {
            throw new IllegalArgumentException("Feature id is required for update.");
        }
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry existing = findFeature(projectId, feature.getFeatureId());
        String oldFileName = featureFileName(existing);
        FeatureStore.update(existing.id(), feature);
        FeatureStore.FeatureEntry updated = findFeature(projectId, feature.getFeatureId());
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.listByFeature(updated.id());
        if (!scenarios.isEmpty()) {
            try {
                regenerateFeatureArtifacts(project, updated, scenarios.get(0));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to regenerate feature files.", e);
            }
        }
        String newFileName = featureFileName(updated);
        if (!oldFileName.equals(newFileName)) {
            deleteFeatureFile(project, oldFileName);
        }
        return "redirect:" + featureReturnUrl(projectId, updated.id(), sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/delete/{featureId}")
    public String deleteFeature(@PathVariable("projectId") String projectId,
                                @PathVariable("featureId") String featureId,
                                @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        String fileName = featureFileName(feature);
        ScenarioStore.deleteByFeature(featureId);
        FeatureStore.delete(featureId);
        deleteFeatureFile(project, fileName);
        return "redirect:" + featureReturnUrl(projectId, null, sourcePage);
    }

    @GetMapping("/projects/{projectId}/features/{featureId}/scenarios")
    public String scenarios(@PathVariable("projectId") String projectId,
                            @PathVariable("featureId") String featureId) {
        ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        findFeature(projectId, featureId);
        return "redirect:/projects/" + projectId + "/features#feature-" + featureId;
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/save")
    public String saveScenario(@PathVariable("projectId") String projectId,
                               @PathVariable("featureId") String featureId,
                               @RequestParam(value = "sourcePage", required = false) String sourcePage,
                               @ModelAttribute("scenario") ScenarioRequest scenario) throws IOException {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        ScenarioStore.ScenarioEntry entry = ScenarioStore.save(projectId, featureId, scenario);
        regenerateFeatureArtifacts(project, feature, entry);
        return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/update")
    public String updateScenario(@PathVariable("projectId") String projectId,
                                 @PathVariable("featureId") String featureId,
                                 @RequestParam(value = "sourcePage", required = false) String sourcePage,
                                 @ModelAttribute("scenario") ScenarioRequest scenario) throws IOException {
        if (scenario.getScenarioId() == null || scenario.getScenarioId().isBlank()) {
            throw new IllegalArgumentException("Scenario id is required for update.");
        }
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        ScenarioStore.update(scenario.getScenarioId(), scenario);
        ScenarioStore.ScenarioEntry entry = ScenarioStore.findById(scenario.getScenarioId())
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + scenario.getScenarioId()));
        regenerateFeatureArtifacts(project, feature, entry);
        return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/delete/{id}")
    public String deleteScenario(@PathVariable("projectId") String projectId,
                                 @PathVariable("featureId") String featureId,
                                 @PathVariable("id") String id,
                                 @RequestParam(value = "sourcePage", required = false) String sourcePage) throws IOException {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        ScenarioStore.delete(id);
        List<ScenarioStore.ScenarioEntry> remaining = ScenarioStore.listByFeature(featureId);
        if (!remaining.isEmpty()) {
            regenerateFeatureArtifacts(project, feature, remaining.get(0));
        } else {
            deleteFeatureFile(project, featureFileName(feature));
        }
        return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/run/{id}")
    public String runScenario(@PathVariable("projectId") String projectId,
                              @PathVariable("featureId") String featureId,
                              @PathVariable("id") String id,
                              @RequestParam(value = "sourcePage", required = false) String sourcePage,
                              Model model) throws IOException {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        ScenarioStore.ScenarioEntry entry = ScenarioStore.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
        GenerateRequest request = buildGenerateRequest(project, feature, entry);
        TemplateGenerator.GenerationResult result = generator.generate(request);
        Path featureFile = result.getProjectRoot()
                .resolve("src/test/resources/features")
                .resolve(result.getFeatureFileName());
        String runId = TestRunService.start(result.getProjectRoot(), request.getScenarioTags(),
                request.getMvnArgs(), request.getScenarioName(), featureFile, entry.id());
        model.addAttribute("runId", runId);
        model.addAttribute("scenarioName", request.getScenarioName());
        model.addAttribute("engine", request.getEngine());
        model.addAttribute("returnUrl", batchReturnUrl(projectId, featureId, sourcePage));
        return "progress";
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/run-all")
    public String runAll(@PathVariable("projectId") String projectId,
                         @PathVariable("featureId") String featureId,
                         @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                         @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        findFeature(projectId, featureId);
        var entries = new ArrayList<>(ScenarioStore.listByFeature(featureId));
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/run-selected")
    public String runSelected(@PathVariable("projectId") String projectId,
                              @PathVariable("featureId") String featureId,
                              @RequestParam(value = "scenarioIds", required = false) List<String> scenarioIds,
                              @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                              @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        findFeature(projectId, featureId);
        if (scenarioIds == null || scenarioIds.isEmpty()) {
            return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
        }
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (String id : scenarioIds) {
            ScenarioStore.ScenarioEntry entry = ScenarioStore.findById(id).orElse(null);
            if (entry != null) {
                entries.add(entry);
            }
        }
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + batchReturnUrl(projectId, featureId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/run-all")
    public String runAllFeatures(@PathVariable("projectId") String projectId,
                                 @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                                 @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(projectId)) {
            entries.addAll(ScenarioStore.listByFeature(feature.id()));
        }
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + featuresBatchReturnUrl(projectId, sourcePage);
    }

    @PostMapping("/projects/{projectId}/features/run-selected")
    public String runSelectedFeatures(@PathVariable("projectId") String projectId,
                                      @RequestParam(value = "featureIds", required = false) List<String> featureIds,
                                      @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                                      @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (featureIds == null || featureIds.isEmpty()) {
            return "redirect:" + featuresBatchReturnUrl(projectId, sourcePage);
        }
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (String featureId : featureIds) {
            FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
            entries.addAll(ScenarioStore.listByFeature(feature.id()));
        }
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + featuresBatchReturnUrl(projectId, sourcePage);
    }

    @GetMapping("/projects/{projectId}/features/{featureId}/scenario-statuses")
    public ResponseEntity<List<ScenarioStatusView>> scenarioStatuses(@PathVariable("projectId") String projectId,
                                                                     @PathVariable("featureId") String featureId) {
        reconcileRunStatuses();
        findFeature(projectId, featureId);
        List<ScenarioStatusView> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry entry : ScenarioStore.listByFeature(featureId)) {
            ScenarioStore.RunStatus status = ScenarioStore.getRunStatus(entry.id());
            String state = status == null || status.state() == null || status.state().isBlank()
                    ? "NOT_RUN" : status.state();
            String message = status == null ? "-" : safe(status.message(), "-");
            String updatedAt = status == null ? "" : safe(status.updatedAt(), "");
            rows.add(new ScenarioStatusView(entry.id(), state, message, updatedAt));
        }
        return ResponseEntity.ok(rows);
    }

    private void startBatchRunAsync(ProjectStore.ProjectEntry project,
                                    List<ScenarioStore.ScenarioEntry> entries,
                                    boolean parallel) {
        if (entries.isEmpty()) {
            return;
        }
        String threadName = "batch-run-" + UUID.randomUUID();
        new Thread(() -> runBatch(project, entries, parallel), threadName).start();
    }

    private void runBatch(ProjectStore.ProjectEntry project,
                          List<ScenarioStore.ScenarioEntry> entries,
                          boolean parallel) {
        if (entries.isEmpty()) {
            return;
        }
        if (!parallel || entries.size() == 1) {
            for (ScenarioStore.ScenarioEntry entry : entries) {
                try {
                    runSingle(project, entry, false);
                } catch (Exception e) {
                    ScenarioStore.updateRunStatus(entry.id(), "FAILURE", "Failed to run scenario: " + e.getMessage());
                }
            }
            return;
        }
        int poolSize = Math.min(4, entries.size());
        for (ScenarioStore.ScenarioEntry entry : entries) {
            ScenarioStore.updateRunStatus(entry.id(), "RUNNING", "Queued in parallel run...");
        }
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<BatchRunResult>> futures = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry entry : entries) {
            futures.add(executor.submit(() -> runSingle(project, entry, true)));
        }
        executor.shutdown();
        for (int i = 0; i < futures.size(); i++) {
            ScenarioStore.ScenarioEntry entry = entries.get(i);
            try {
                futures.get(i).get();
            } catch (Exception e) {
                ScenarioStore.updateRunStatus(entry.id(), "FAILURE", "Failed to run scenario: " + e.getMessage());
            }
        }
    }

    private BatchRunResult runSingle(ProjectStore.ProjectEntry project,
                                     ScenarioStore.ScenarioEntry entry,
                                     boolean isolatedWorkspace) throws IOException {
        ScenarioStore.updateRunStatus(entry.id(), "RUNNING", "Running tests...");
        FeatureStore.FeatureEntry feature = FeatureStore.findById(entry.featureId())
                .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + entry.featureId()));
        GenerateRequest request = buildGenerateRequest(project, feature, entry);
        if (isolatedWorkspace) {
            String base = TemplateGenerator.sanitizeProjectName(project.project().getProjectName());
            String suffix = entry.id() == null || entry.id().isBlank() ? UUID.randomUUID().toString() : entry.id();
            String shortId = suffix.length() > 8 ? suffix.substring(0, 8) : suffix;
            request.setProjectName(base + "-parallel-" + shortId + "-" + System.currentTimeMillis());
        }
        TemplateGenerator.GenerationResult result = generator.generate(request);
        Path featureFile = result.getProjectRoot()
                .resolve("src/test/resources/features")
                .resolve(result.getFeatureFileName());
        TestRunResult runResult = TestRunner.run(result.getProjectRoot(),
                request.getScenarioTags(), request.getMvnArgs(), request.getScenarioName(), featureFile);
        String state = runResult.exitCode() == 0 ? "SUCCESS" : "FAILURE";
        ScenarioStore.updateRunStatus(entry.id(), state, runResult.summary());
        return new BatchRunResult(entry.id(), request.getScenarioName(),
                request.getEngine(), runResult.exitCode(), runResult.summary());
    }

    private String batchReturnUrl(String projectId, String featureId, String sourcePage) {
        if ("project-details".equalsIgnoreCase(safe(sourcePage, ""))) {
            return "/projects/" + projectId + "?openFeature=" + featureId;
        }
        return "/projects/" + projectId + "/features#feature-" + featureId;
    }

    private String featuresBatchReturnUrl(String projectId, String sourcePage) {
        if ("project-details".equalsIgnoreCase(safe(sourcePage, ""))) {
            return "/projects/" + projectId;
        }
        return "/projects/" + projectId + "/features";
    }

    private String featureReturnUrl(String projectId, String featureId, String sourcePage) {
        if ("project-details".equalsIgnoreCase(safe(sourcePage, ""))) {
            return "/projects/" + projectId + "?banner=feature_saved";
        }
        if (featureId == null || featureId.isBlank()) {
            return "/projects/" + projectId + "/features";
        }
        return "/projects/" + projectId + "/features#feature-" + featureId;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<TestRunService.TestRunStatus> status(@PathVariable("id") String id) {
        TestRunService.TestRunStatus status = TestRunService.get(id);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(status);
    }

    public record BatchRunResult(String id, String scenarioName, String engine, int exitCode, String summary) {}

    public record ScenarioRow(String id, ScenarioRequest request, ScenarioStore.RunStatus status) {}

    public record FeatureCard(String id, FeatureRequest request, List<ScenarioRow> scenarios, String status) {}

    public record ScenarioStatusView(String id, String state, String message, String updatedAt) {}

    public record ProjectMetrics(int features,
                                 int featuresWithScenarios,
                                 int featuresWithoutScenarios,
                                 int scenarios,
                                 int success,
                                 int failure,
                                 int running,
                                 int notRun) {}

    public record ProjectDashboardRow(String id, ProjectRequest project, ProjectMetrics metrics) {}

    public record RunningTestRow(String scenarioId,
                                 String projectId,
                                 String projectName,
                                 String featureId,
                                 String featureName,
                                 String scenarioName,
                                 String state,
                                 String message,
                                 String updatedAt) {}

    public record FeatureOverviewRow(String featureId,
                                     String projectId,
                                     String projectName,
                                     String featureName,
                                     String description,
                                     String baseUrl,
                                     String jiraStories,
                                     int scenarioCount) {}

    public record ScenarioOverviewRow(String scenarioId,
                                      String projectId,
                                      String projectName,
                                      String featureId,
                                      String featureName,
                                      String scenarioName,
                                      String tags,
                                      String state,
                                      String message,
                                      String updatedAt) {}

    public record ProjectScenarioRow(String scenarioId,
                                     String featureId,
                                     String featureName,
                                     String scenarioName,
                                     String tags,
                                     String state,
                                     String message,
                                     String updatedAt) {}

    public record ProjectExecutionRow(String scenarioId,
                                      String featureId,
                                      String featureName,
                                      String scenarioName,
                                      String state,
                                      String message,
                                      String updatedAt) {}

    private List<ScenarioRow> scenarioRows(String featureId) {
        List<ScenarioRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry entry : ScenarioStore.listByFeature(featureId)) {
            rows.add(new ScenarioRow(entry.id(), entry.request(), ScenarioStore.getRunStatus(entry.id())));
        }
        return rows;
    }

    private List<FeatureCard> featureCards(String projectId) {
        List<FeatureCard> rows = new ArrayList<>();
        for (FeatureStore.FeatureEntry entry : FeatureStore.listByProject(projectId)) {
            List<ScenarioRow> scenarios = scenarioRows(entry.id());
            rows.add(new FeatureCard(entry.id(), entry.request(), scenarios, aggregateFeatureStatus(scenarios)));
        }
        return rows;
    }

    private String aggregateFeatureStatus(List<ScenarioRow> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return "RUN";
        }
        boolean anyRunning = false;
        boolean anyFailure = false;
        boolean anySuccess = false;
        for (ScenarioRow row : scenarios) {
            ScenarioStore.RunStatus status = row.status();
            String state = status == null || status.state() == null || status.state().isBlank()
                    ? "NOT_RUN" : status.state().trim().toUpperCase();
            if ("RUNNING".equals(state)) {
                anyRunning = true;
            } else if ("FAILURE".equals(state)) {
                anyFailure = true;
            } else if ("SUCCESS".equals(state)) {
                anySuccess = true;
            }
        }
        if (anyRunning) {
            return "IN_PROGRESS";
        }
        if (anyFailure) {
            return "FAIL";
        }
        if (anySuccess) {
            return "PASS";
        }
        return "RUN";
    }

    private List<ProjectDashboardRow> projectDashboardRows() {
        List<ProjectDashboardRow> rows = new ArrayList<>();
        for (ProjectStore.ProjectEntry project : ProjectStore.list()) {
            List<FeatureStore.FeatureEntry> features = FeatureStore.listByProject(project.id());
            int featuresWithScenarios = 0;
            int scenarioCount = 0;
            int success = 0;
            int failure = 0;
            int running = 0;
            int notRun = 0;
            for (FeatureStore.FeatureEntry feature : features) {
                List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.listByFeature(feature.id());
                if (!scenarios.isEmpty()) {
                    featuresWithScenarios++;
                }
                for (ScenarioStore.ScenarioEntry scenario : scenarios) {
                    scenarioCount++;
                    ScenarioStore.RunStatus runStatus = ScenarioStore.getRunStatus(scenario.id());
                    if (runStatus == null || runStatus.state() == null) {
                        notRun++;
                        continue;
                    }
                    String state = runStatus.state().trim().toUpperCase();
                    if ("SUCCESS".equals(state)) {
                        success++;
                    } else if ("FAILURE".equals(state)) {
                        failure++;
                    } else if ("RUNNING".equals(state)) {
                        running++;
                    } else {
                        notRun++;
                    }
                }
            }
            int featureCount = features.size();
            ProjectMetrics metrics = new ProjectMetrics(featureCount, featuresWithScenarios,
                    Math.max(0, featureCount - featuresWithScenarios), scenarioCount, success, failure, running, notRun);
            rows.add(new ProjectDashboardRow(project.id(), project.project(), metrics));
        }
        return rows;
    }

    private void reconcileRunStatuses() {
        // Close stale RUNNING rows so UI does not show ghost runs after interrupted sessions.
        ScenarioStore.markStaleRunningAsFailed(600);
    }

    private List<RunningTestRow> runningTestRows() {
        Map<String, ProjectStore.ProjectEntry> projectById = new HashMap<>();
        for (ProjectStore.ProjectEntry project : ProjectStore.list()) {
            projectById.put(project.id(), project);
        }

        Map<String, FeatureStore.FeatureEntry> featureById = new HashMap<>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.list()) {
            featureById.put(feature.id(), feature);
        }

        List<RunningTestRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.list()) {
            ProjectStore.ProjectEntry project = projectById.get(scenario.projectId());
            FeatureStore.FeatureEntry feature = featureById.get(scenario.featureId());
            ScenarioStore.RunStatus status = ScenarioStore.getRunStatus(scenario.id());
            String state = status == null || status.state() == null || status.state().isBlank()
                    ? "NOT_RUN" : status.state().trim().toUpperCase();
            String message = status == null ? "-" : safe(status.message(), "-");
            String updatedAt = status == null ? "" : safe(status.updatedAt(), "");
            rows.add(new RunningTestRow(
                    scenario.id(),
                    scenario.projectId(),
                    project == null ? "-" : safe(project.project().getProjectName(), "-"),
                    scenario.featureId(),
                    feature == null ? "-" : safe(feature.request().getFeatureName(), "-"),
                    safe(scenario.request().getScenarioName(), "-"),
                    state,
                    message,
                    updatedAt
            ));
        }
        return rows;
    }

    private List<FeatureOverviewRow> featureOverviewRows() {
        Map<String, ProjectStore.ProjectEntry> projectById = new HashMap<>();
        for (ProjectStore.ProjectEntry project : ProjectStore.list()) {
            projectById.put(project.id(), project);
        }
        List<FeatureOverviewRow> rows = new ArrayList<>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.list()) {
            ProjectStore.ProjectEntry project = projectById.get(feature.projectId());
            rows.add(new FeatureOverviewRow(
                    feature.id(),
                    feature.projectId(),
                    project == null ? "-" : safe(project.project().getProjectName(), "-"),
                    safe(feature.request().getFeatureName(), "-"),
                    safe(feature.request().getFeatureDescription(), ""),
                    safe(feature.request().getBaseUrl(), "-"),
                    safe(feature.request().getJiraUserStories(), "-"),
                    ScenarioStore.listByFeature(feature.id()).size()
            ));
        }
        return rows;
    }

    private List<ScenarioOverviewRow> scenarioOverviewRows() {
        Map<String, ProjectStore.ProjectEntry> projectById = new HashMap<>();
        for (ProjectStore.ProjectEntry project : ProjectStore.list()) {
            projectById.put(project.id(), project);
        }
        Map<String, FeatureStore.FeatureEntry> featureById = new HashMap<>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.list()) {
            featureById.put(feature.id(), feature);
        }

        List<ScenarioOverviewRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.list()) {
            ProjectStore.ProjectEntry project = projectById.get(scenario.projectId());
            FeatureStore.FeatureEntry feature = featureById.get(scenario.featureId());
            ScenarioStore.RunStatus status = ScenarioStore.getRunStatus(scenario.id());
            String state = status == null || status.state() == null || status.state().isBlank()
                    ? "NOT_RUN" : status.state().trim().toUpperCase();
            rows.add(new ScenarioOverviewRow(
                    scenario.id(),
                    scenario.projectId(),
                    project == null ? "-" : safe(project.project().getProjectName(), "-"),
                    scenario.featureId(),
                    feature == null ? "-" : safe(feature.request().getFeatureName(), "-"),
                    safe(scenario.request().getScenarioName(), "-"),
                    safe(scenario.request().getScenarioTags(), ""),
                    state,
                    status == null ? "-" : safe(status.message(), "-"),
                    status == null ? "" : safe(status.updatedAt(), "")
            ));
        }
        return rows;
    }

    private List<ProjectScenarioRow> projectScenarioRows(String projectId) {
        List<ProjectScenarioRow> rows = new ArrayList<>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(projectId)) {
            String featureName = safe(feature.request().getFeatureName(), "-");
            for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.listByFeature(feature.id())) {
                ScenarioStore.RunStatus status = ScenarioStore.getRunStatus(scenario.id());
                String runState = status == null || status.state() == null || status.state().isBlank()
                        ? "NOT_RUN" : status.state().trim().toUpperCase();
                String message = status == null ? "-" : safe(status.message(), "-");
                String updatedAt = status == null ? "" : safe(status.updatedAt(), "");
                rows.add(new ProjectScenarioRow(
                        scenario.id(),
                        feature.id(),
                        featureName,
                        safe(scenario.request().getScenarioName(), "-"),
                        safe(scenario.request().getScenarioTags(), ""),
                        runState,
                        message,
                        updatedAt
                ));
            }
        }
        return rows;
    }

    private List<ProjectExecutionRow> projectExecutionRows(String projectId, String stateFilter) {
        List<ProjectExecutionRow> rows = new ArrayList<>();
        String normalizedFilter = stateFilter == null ? "" : stateFilter.trim().toUpperCase();
        for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(projectId)) {
            String featureName = safe(feature.request().getFeatureName(), "-");
            for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.listByFeature(feature.id())) {
                ScenarioStore.RunStatus status = ScenarioStore.getRunStatus(scenario.id());
                if (status == null || status.state() == null || status.state().isBlank()) {
                    continue;
                }
                String runState = status.state().trim().toUpperCase();
                if ("NOT_RUN".equals(runState)) {
                    continue;
                }
                if (!normalizedFilter.isBlank() && !normalizedFilter.equals(runState)) {
                    continue;
                }
                rows.add(new ProjectExecutionRow(
                        scenario.id(),
                        feature.id(),
                        featureName,
                        safe(scenario.request().getScenarioName(), "-"),
                        runState,
                        safe(status.message(), "-"),
                        safe(status.updatedAt(), "")
                ));
            }
        }
        return rows;
    }

    private FeatureStore.FeatureEntry findFeature(String projectId, String featureId) {
        FeatureStore.FeatureEntry feature = FeatureStore.findById(featureId)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + featureId));
        if (!feature.projectId().equals(projectId)) {
            throw new IllegalArgumentException("Feature does not belong to project: " + projectId);
        }
        return feature;
    }

    private GenerateRequest buildGenerateRequest(ProjectStore.ProjectEntry project,
                                                 FeatureStore.FeatureEntry feature,
                                                 ScenarioStore.ScenarioEntry entry) {
        ProjectRequest p = project.project();
        FeatureRequest f = feature.request();
        ScenarioRequest s = entry.request();
        GenerateRequest request = new GenerateRequest();
        request.setProjectName(p.getProjectName());
        request.setEngine(p.getEngine());
        request.setBrowser(p.getBrowser());
        request.setHeadless(p.isHeadless());
        request.setChromeStartMaximized(p.isChromeStartMaximized());
        request.setChromeIncognito(p.isChromeIncognito());
        request.setChromeDisableNotifications(p.isChromeDisableNotifications());
        request.setChromeDisablePopupBlocking(p.isChromeDisablePopupBlocking());
        request.setChromeAcceptInsecureCerts(p.isChromeAcceptInsecureCerts());
        request.setChromeCustomArgs(p.getChromeCustomArgs());
        request.setFirefoxPrivateMode(p.isFirefoxPrivateMode());
        request.setFirefoxAcceptInsecureCerts(p.isFirefoxAcceptInsecureCerts());
        request.setFirefoxCustomArgs(p.getFirefoxCustomArgs());
        request.setEdgeStartMaximized(p.isEdgeStartMaximized());
        request.setEdgeInPrivate(p.isEdgeInPrivate());
        request.setEdgeAcceptInsecureCerts(p.isEdgeAcceptInsecureCerts());
        request.setEdgeCustomArgs(p.getEdgeCustomArgs());
        String scenarioUrl = s.getBaseUrl();
        String baseUrl = scenarioUrl == null || scenarioUrl.isBlank() ? f.getBaseUrl() : scenarioUrl;
        request.setBaseUrl(baseUrl == null ? "" : baseUrl);
        request.setFeatureName(f.getFeatureName());
        request.setFeatureId(feature.id());
        request.setScenarioName(s.getScenarioName());
        request.setScenarioSteps(s.getScenarioSteps());
        request.setScenarioTags(s.getScenarioTags());
        request.setMvnArgs(s.getMvnArgs());
        request.setProjectMode(true);
        List<ScenarioRequest> featureScenarios = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenarioEntry : ScenarioStore.listByFeature(feature.id())) {
            featureScenarios.add(scenarioEntry.request().copy());
        }
        request.setFeatureScenarios(featureScenarios);
        return request;
    }

    private void regenerateFeatureArtifacts(ProjectStore.ProjectEntry project,
                                            FeatureStore.FeatureEntry feature,
                                            ScenarioStore.ScenarioEntry entry) throws IOException {
        GenerateRequest request = buildGenerateRequest(project, feature, entry);
        generator.generate(request);
    }

    private String featureFileName(FeatureStore.FeatureEntry feature) {
        String safeId = TemplateGenerator.sanitizeProjectName(feature.id()).toLowerCase();
        String safeName = TemplateGenerator.sanitizeProjectName(feature.request().getFeatureName()).toLowerCase();
        StringBuilder name = new StringBuilder("feature");
        if (!safeId.isBlank()) {
            name.append("-").append(safeId);
        }
        if (!safeName.isBlank()) {
            name.append("-").append(safeName);
        }
        return name.append(".feature").toString();
    }

    private void deleteFeatureFile(ProjectStore.ProjectEntry project, String featureFileName) {
        try {
            String safeProject = TemplateGenerator.sanitizeProjectName(project.project().getProjectName());
            Path file = AppPaths.generatedDir().toAbsolutePath()
                    .resolve(safeProject)
                    .resolve("src/test/resources/features")
                    .resolve(featureFileName);
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void deleteProjectDir(String projectName) {
        String safeProject = TemplateGenerator.sanitizeProjectName(projectName);
        Path projectDir = AppPaths.generatedDir().toAbsolutePath().resolve(safeProject);
        if (!Files.exists(projectDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(projectDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    private void normalizeChromeOptions(ProjectRequest project) {
        String engine = project.getEngine() == null ? "" : project.getEngine().trim().toLowerCase();
        String browser = project.getBrowser() == null ? "" : project.getBrowser().trim().toLowerCase();
        if (!"selenium".equals(engine)) {
            project.setChromeCustomArgs("");
            project.setFirefoxCustomArgs("");
            project.setEdgeCustomArgs("");
            return;
        }
        if ("chrome".equals(browser)) {
            project.setChromeCustomArgs(sanitizeCustomArgs(project.getChromeCustomArgs(), BLOCKED_CHROME_ARG_PREFIXES));
            project.setFirefoxCustomArgs("");
            project.setEdgeCustomArgs("");
            return;
        }
        if ("firefox".equals(browser)) {
            project.setFirefoxCustomArgs(sanitizeCustomArgs(project.getFirefoxCustomArgs(), Set.of()));
            project.setChromeCustomArgs("");
            project.setEdgeCustomArgs("");
            return;
        }
        if ("edge".equals(browser)) {
            project.setEdgeCustomArgs(sanitizeCustomArgs(project.getEdgeCustomArgs(), BLOCKED_CHROME_ARG_PREFIXES));
            project.setChromeCustomArgs("");
            project.setFirefoxCustomArgs("");
            return;
        }
        project.setChromeCustomArgs("");
        project.setFirefoxCustomArgs("");
        project.setEdgeCustomArgs("");
    }

    private String sanitizeCustomArgs(String raw, Set<String> blockedPrefixes) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        String normalized = raw.replace("\r", "\n");
        String[] lines = normalized.split("\n");
        for (String line : lines) {
            String[] parts = line.split(",");
            for (String part : parts) {
                String value = part.trim();
                if (value.isBlank()) {
                    continue;
                }
                if (!value.startsWith("-")) {
                    value = "--" + value;
                }
                String lower = value.toLowerCase();
                boolean blocked = blockedPrefixes.stream().anyMatch(lower::startsWith);
                if (!blocked) {
                    sanitized.add(value);
                }
            }
        }
        return String.join("\n", sanitized);
    }

}
