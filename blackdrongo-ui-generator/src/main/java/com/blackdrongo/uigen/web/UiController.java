package com.blackdrongo.uigen.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Controller
public class UiController {

    private static final DateTimeFormatter RUN_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> BLOCKED_CHROME_ARG_PREFIXES = Set.of(
            "--user-data-dir",
            "--profile-directory",
            "--remote-debugging-port"
    );
    private final TemplateGenerator generator = new TemplateGenerator();
    private final AiServiceClient aiServiceClient;

    public UiController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

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
        ProjectStore.ProjectEntry updated = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        regenerateProjectArtifacts(updated);
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
        ProjectStore.ProjectEntry saved = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        regenerateProjectArtifacts(saved);
        return "redirect:/projects";
    }

    @GetMapping("/projects/{projectId}/features")
    public String features(@PathVariable("projectId") String projectId, Model model) {
        reconcileRunStatuses();
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        ProjectSnapshot snapshot = projectSnapshot(projectId);
        FeatureRequest feature = new FeatureRequest();
        feature.setFeatureName("Main Feature");
        feature.setFeatureDescription("");
        feature.setBaseUrl("");
        feature.setJiraUserStories("");
        model.addAttribute("project", project);
        model.addAttribute("projectCards", projectDashboardRows());
        model.addAttribute("feature", feature);
        model.addAttribute("featureCards", featureCards(snapshot));
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
        ProjectSnapshot snapshot = projectSnapshot(projectId);
        List<FeatureStore.FeatureEntry> features = snapshot.features();
        String stateFilter = state == null ? "" : state.trim().toUpperCase();
        List<ProjectExecutionRow> executionRows = projectExecutionRows(snapshot, stateFilter);
        ExecutionSummary executionSummary = buildExecutionSummary(executionRows, 10);
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
        model.addAttribute("featureCards", featureCards(snapshot));
        model.addAttribute("featureRows", features);
        model.addAttribute("feature", feature);
        model.addAttribute("scenario", scenario);
        model.addAttribute("executionRows", executionRows);
        model.addAttribute("latestExecutionRows", executionSummary.latestRows());
        model.addAttribute("executionSummary", executionSummary);
        model.addAttribute("hasOlderExecutionRows", executionSummary.totalRows() > executionSummary.windowSize());
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
        ProjectSnapshot snapshot = projectSnapshot(projectId);
        String stateFilter = state == null ? "" : state.trim().toUpperCase();
        model.addAttribute("project", project);
        model.addAttribute("projectCards", projectDashboardRows());
        model.addAttribute("stateFilter", stateFilter);
        model.addAttribute("executionRows", projectExecutionRows(snapshot, stateFilter));
        return "project-executions";
    }

    @GetMapping("/projects/{projectId}/executions/summary")
    public ResponseEntity<ExecutionSummary> projectExecutionSummary(@PathVariable("projectId") String projectId,
                                                                    @RequestParam(value = "limit", defaultValue = "10") int limit) {
        reconcileRunStatuses();
        ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return ResponseEntity.ok(buildExecutionSummary(projectExecutionRows(projectSnapshot(projectId), ""), safeLimit));
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
                            @PathVariable("featureId") String featureId,
                            Model model) {
        reconcileRunStatuses();
        ProjectStore.ProjectEntry project = ProjectStore.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(projectId, featureId);
        ProjectSnapshot snapshot = projectSnapshot(projectId);
        ScenarioRequest scenario = new ScenarioRequest();
        scenario.setScenarioName("Open the home page");
        scenario.setScenarioSteps("Given I open the application");
        scenario.setScenarioTags("@smoke");
        scenario.setMvnArgs("");
        model.addAttribute("project", project);
        model.addAttribute("feature", feature);
        model.addAttribute("scenarioRows", scenarioRows(
                snapshot.scenariosByFeature().getOrDefault(featureId, List.of()),
                snapshot.statuses()
        ));
        model.addAttribute("scenario", scenario);
        return "scenarios";
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

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/generate-ai")
    public ResponseEntity<AiScenarioGenerationResponse> generateScenarioWithAi(@PathVariable("projectId") String projectId,
                                                                               @PathVariable("featureId") String featureId,
                                                                               @RequestBody ScenarioRequest scenario) {
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(normalizedProjectId, normalizedFeatureId);
        AiServiceClient.GeneratedScenarioDraft draft = aiServiceClient.generateScenario(project, feature, scenario);
        return ResponseEntity.ok(new AiScenarioGenerationResponse(
                draft.scenarioSteps(),
                draft.provider(),
                draft.assumptions(),
                draft.stepDefinitionsPreview(),
                draft.pageObjectPreview()
        ));
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
    public Object runScenario(@PathVariable("projectId") String projectId,
                              @PathVariable("featureId") String featureId,
                              @PathVariable("id") String id,
                              @RequestParam(value = "sourcePage", required = false) String sourcePage) throws IOException {
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        FeatureStore.FeatureEntry feature = findFeature(normalizedProjectId, normalizedFeatureId);
        ScenarioStore.ScenarioEntry entry = ScenarioStore.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
        GenerateRequest request = buildGenerateRequest(project, feature, entry);
        TemplateGenerator.GenerationResult result = generator.generate(request);
        Path featureFile = result.getProjectRoot()
                .resolve("src/test/resources/features")
                .resolve(result.getFeatureFileName());
        TestRunService.start(result.getProjectRoot(), request.getScenarioTags(),
                request.getMvnArgs(), request.getScenarioName(), featureFile, entry.id());
        if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
            return ResponseEntity.ok(executionBannerPayload(1, false, true));
        }
        String redirect = singleScenarioRunReturnUrl(normalizedProjectId, normalizedFeatureId, sourcePage);
        return "redirect:" + withExecutionBanner(redirect, 1, false, true);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/run-all")
    public Object runAll(@PathVariable("projectId") String projectId,
                         @PathVariable("featureId") String featureId,
                         @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                         @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        findFeature(normalizedProjectId, normalizedFeatureId);
        var entries = new ArrayList<>(ScenarioStore.listByFeature(normalizedFeatureId));
        String redirect = runReturnUrl(normalizedProjectId, normalizedFeatureId, sourcePage);
        if (entries.isEmpty()) {
            if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
                return ResponseEntity.ok(executionBannerPayload(0, parallel, false));
            }
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        startBatchRunAsync(project, entries, parallel);
        if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
            return ResponseEntity.ok(executionBannerPayload(entries.size(), parallel, true));
        }
        return "redirect:" + withExecutionBanner(redirect, entries.size(), parallel, true);
    }

    @PostMapping("/projects/{projectId}/features/{featureId}/scenarios/run-selected")
    public Object runSelected(@PathVariable("projectId") String projectId,
                              @PathVariable("featureId") String featureId,
                              @RequestParam(value = "scenarioIds", required = false) List<String> scenarioIds,
                              @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                              @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        findFeature(normalizedProjectId, normalizedFeatureId);
        String redirect = runReturnUrl(normalizedProjectId, normalizedFeatureId, sourcePage);
        if (scenarioIds == null || scenarioIds.isEmpty()) {
            if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
                return ResponseEntity.ok(executionBannerPayload(0, parallel, false));
            }
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (String id : scenarioIds) {
            ScenarioStore.ScenarioEntry entry = ScenarioStore.findById(id).orElse(null);
            if (entry != null) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
                return ResponseEntity.ok(executionBannerPayload(0, parallel, false));
            }
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        startBatchRunAsync(project, entries, parallel);
        if ("popup".equalsIgnoreCase(safe(sourcePage, ""))) {
            return ResponseEntity.ok(executionBannerPayload(entries.size(), parallel, true));
        }
        return "redirect:" + withExecutionBanner(redirect, entries.size(), parallel, true);
    }

    @PostMapping("/projects/{projectId}/features/run-all")
    public String runAllFeatures(@PathVariable("projectId") String projectId,
                                 @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                                 @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        String normalizedProjectId = normalizePathId(projectId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(normalizedProjectId)) {
            entries.addAll(ScenarioStore.listByFeature(feature.id()));
        }
        String redirect = featuresBatchReturnUrl(normalizedProjectId, sourcePage);
        if (entries.isEmpty()) {
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + withExecutionBanner(redirect, entries.size(), parallel, true);
    }

    @PostMapping("/projects/{projectId}/features/run-selected")
    public String runSelectedFeatures(@PathVariable("projectId") String projectId,
                                      @RequestParam(value = "featureIds", required = false) List<String> featureIds,
                                      @RequestParam(value = "parallel", defaultValue = "false") boolean parallel,
                                      @RequestParam(value = "sourcePage", required = false) String sourcePage) {
        String normalizedProjectId = normalizePathId(projectId);
        ProjectStore.ProjectEntry project = ProjectStore.findById(normalizedProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        String redirect = featuresBatchReturnUrl(normalizedProjectId, sourcePage);
        if (featureIds == null || featureIds.isEmpty()) {
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        var entries = new ArrayList<ScenarioStore.ScenarioEntry>();
        for (String featureId : featureIds) {
            FeatureStore.FeatureEntry feature = findFeature(normalizedProjectId, featureId);
            entries.addAll(ScenarioStore.listByFeature(feature.id()));
        }
        if (entries.isEmpty()) {
            return "redirect:" + withExecutionBanner(redirect, 0, parallel, false);
        }
        startBatchRunAsync(project, entries, parallel);
        return "redirect:" + withExecutionBanner(redirect, entries.size(), parallel, true);
    }

    @GetMapping("/projects/{projectId}/features/{featureId}/scenario-statuses")
    public ResponseEntity<List<ScenarioStatusView>> scenarioStatuses(@PathVariable("projectId") String projectId,
                                                                     @PathVariable("featureId") String featureId) {
        reconcileRunStatuses();
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        findFeature(normalizedProjectId, normalizedFeatureId);
        List<ScenarioStatusView> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry entry : ScenarioStore.listByFeature(normalizedFeatureId)) {
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
        if ("scenarios".equalsIgnoreCase(safe(sourcePage, ""))) {
            return scenarioPageUrl(projectId, featureId);
        }
        if ("project-details".equalsIgnoreCase(safe(sourcePage, ""))) {
            return "/projects/" + projectId + "?openFeature=" + featureId;
        }
        return "/projects/" + projectId + "/features#feature-" + featureId;
    }

    private String runReturnUrl(String projectId, String featureId, String sourcePage) {
        if ("scenarios".equalsIgnoreCase(safe(sourcePage, ""))) {
            return scenarioPageUrl(projectId, featureId);
        }
        if ("project-details".equalsIgnoreCase(safe(sourcePage, ""))) {
            return "/projects/" + projectId + "?openFeature=" + featureId;
        }
        return "/projects/" + projectId + "/features#feature-" + featureId;
    }

    private String singleScenarioRunReturnUrl(String projectId, String featureId, String sourcePage) {
        if ("scenarios".equalsIgnoreCase(safe(sourcePage, ""))) {
            return scenarioPageUrl(projectId, featureId);
        }
        return "/projects/" + projectId + "?openFeature=" + featureId;
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

    private String withExecutionBanner(String url, int queued, boolean parallel, boolean started) {
        String query = "banner=" + (started ? "execution_queued" : "execution_none")
                + "&queued=" + Math.max(0, queued)
                + "&runMode=" + (parallel ? "parallel" : "sequential");
        return appendQuery(url, query);
    }

    private Map<String, Object> executionBannerPayload(int queued, boolean parallel, boolean started) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("banner", started ? "execution_queued" : "execution_none");
        payload.put("queued", Math.max(0, queued));
        payload.put("runMode", parallel ? "parallel" : "sequential");
        payload.put("started", started);
        payload.put("message", started
                ? "Execution picked up for " + Math.max(0, queued) + " scenario(s) in " + (parallel ? "parallel" : "sequential") + " mode."
                : "No scenarios were selected/available to run. Mode: " + (parallel ? "parallel" : "sequential") + ".");
        return payload;
    }

    private String appendQuery(String url, String query) {
        int hash = url.indexOf('#');
        String base = hash >= 0 ? url.substring(0, hash) : url;
        String fragment = hash >= 0 ? url.substring(hash) : "";
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + query + fragment;
    }

    private String scenarioPageUrl(String projectId, String featureId) {
        return "/projects/" + projectId + "/features/" + featureId + "/scenarios";
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

    private List<ScenarioRow> scenarioRows(String featureId) {
        Map<String, ScenarioStore.RunStatus> statuses = ScenarioStore.listRunStatuses();
        return scenarioRows(ScenarioStore.listByFeature(featureId), statuses);
    }

    private List<ScenarioRow> scenarioRows(List<ScenarioStore.ScenarioEntry> scenarios,
                                           Map<String, ScenarioStore.RunStatus> statuses) {
        List<ScenarioRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry entry : scenarios) {
            rows.add(new ScenarioRow(entry.id(), entry.request(), statuses.get(entry.id())));
        }
        return rows;
    }

    private List<FeatureCard> featureCards(String projectId) {
        return featureCards(projectSnapshot(projectId));
    }

    private List<FeatureCard> featureCards(ProjectSnapshot snapshot) {
        List<FeatureCard> rows = new ArrayList<>();
        for (FeatureStore.FeatureEntry entry : snapshot.features()) {
            List<ScenarioRow> featureScenarios = scenarioRows(
                    snapshot.scenariosByFeature().getOrDefault(entry.id(), List.of()),
                    snapshot.statuses()
            );
            rows.add(new FeatureCard(entry.id(), entry.request(), featureScenarios, aggregateFeatureStatus(featureScenarios)));
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
        List<ProjectStore.ProjectEntry> projects = ProjectStore.list();
        List<FeatureStore.FeatureEntry> features = FeatureStore.list();
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.list();
        Map<String, List<FeatureStore.FeatureEntry>> featuresByProject = featuresByProject(features);
        Map<String, Integer> scenarioCountByFeature = new HashMap<>();
        for (ScenarioStore.ScenarioEntry scenario : scenarios) {
            scenarioCountByFeature.merge(scenario.featureId(), 1, Integer::sum);
        }
        Map<String, ScenarioStore.RunStatus> statuses = ScenarioStore.listRunStatuses();
        List<ProjectDashboardRow> rows = new ArrayList<>();
        for (ProjectStore.ProjectEntry project : projects) {
            List<FeatureStore.FeatureEntry> projectFeatures = featuresByProject.getOrDefault(project.id(), List.of());
            int featuresWithScenarios = 0;
            int scenarioCount = 0;
            int success = 0;
            int failure = 0;
            int running = 0;
            int notRun = 0;
            for (FeatureStore.FeatureEntry feature : projectFeatures) {
                int featureScenarioCount = scenarioCountByFeature.getOrDefault(feature.id(), 0);
                if (featureScenarioCount > 0) {
                    featuresWithScenarios++;
                }
            }
            for (ScenarioStore.ScenarioEntry scenario : scenarios) {
                if (!project.id().equals(scenario.projectId())) {
                    continue;
                }
                scenarioCount++;
                ScenarioStore.RunStatus runStatus = statuses.get(scenario.id());
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
            int featureCount = projectFeatures.size();
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
        List<ProjectStore.ProjectEntry> projects = ProjectStore.list();
        List<FeatureStore.FeatureEntry> features = FeatureStore.list();
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.list();
        Map<String, ProjectStore.ProjectEntry> projectById = projectById(projects);
        Map<String, FeatureStore.FeatureEntry> featureById = featureById(features);
        Map<String, ScenarioStore.RunStatus> statuses = ScenarioStore.listRunStatuses();

        List<RunningTestRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : scenarios) {
            ProjectStore.ProjectEntry project = projectById.get(scenario.projectId());
            FeatureStore.FeatureEntry feature = featureById.get(scenario.featureId());
            ScenarioStore.RunStatus status = statuses.get(scenario.id());
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
        List<ProjectStore.ProjectEntry> projects = ProjectStore.list();
        List<FeatureStore.FeatureEntry> features = FeatureStore.list();
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.list();
        Map<String, ProjectStore.ProjectEntry> projectById = projectById(projects);
        Map<String, Integer> scenarioCountByFeature = new HashMap<>();
        for (ScenarioStore.ScenarioEntry scenario : scenarios) {
            scenarioCountByFeature.merge(scenario.featureId(), 1, Integer::sum);
        }
        List<FeatureOverviewRow> rows = new ArrayList<>();
        for (FeatureStore.FeatureEntry feature : features) {
            ProjectStore.ProjectEntry project = projectById.get(feature.projectId());
            rows.add(new FeatureOverviewRow(
                    feature.id(),
                    feature.projectId(),
                    project == null ? "-" : safe(project.project().getProjectName(), "-"),
                    safe(feature.request().getFeatureName(), "-"),
                    safe(feature.request().getFeatureDescription(), ""),
                    safe(feature.request().getBaseUrl(), "-"),
                    safe(feature.request().getJiraUserStories(), "-"),
                    scenarioCountByFeature.getOrDefault(feature.id(), 0)
            ));
        }
        return rows;
    }

    private List<ScenarioOverviewRow> scenarioOverviewRows() {
        List<ProjectStore.ProjectEntry> projects = ProjectStore.list();
        List<FeatureStore.FeatureEntry> features = FeatureStore.list();
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.list();
        Map<String, ProjectStore.ProjectEntry> projectById = projectById(projects);
        Map<String, FeatureStore.FeatureEntry> featureById = featureById(features);
        Map<String, ScenarioStore.RunStatus> statuses = ScenarioStore.listRunStatuses();

        List<ScenarioOverviewRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : scenarios) {
            ProjectStore.ProjectEntry project = projectById.get(scenario.projectId());
            FeatureStore.FeatureEntry feature = featureById.get(scenario.featureId());
            ScenarioStore.RunStatus status = statuses.get(scenario.id());
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
        return projectScenarioRows(projectSnapshot(projectId));
    }

    private List<ProjectScenarioRow> projectScenarioRows(ProjectSnapshot snapshot) {
        List<ProjectScenarioRow> rows = new ArrayList<>();
        for (ScenarioStore.ScenarioEntry scenario : snapshot.scenarios()) {
            FeatureStore.FeatureEntry feature = snapshot.featureById().get(scenario.featureId());
            String featureName = feature == null ? "-" : safe(feature.request().getFeatureName(), "-");
            ScenarioStore.RunStatus status = snapshot.statuses().get(scenario.id());
            String runState = status == null || status.state() == null || status.state().isBlank()
                    ? "NOT_RUN" : status.state().trim().toUpperCase();
            String message = status == null ? "-" : safe(status.message(), "-");
            String updatedAt = status == null ? "" : safe(status.updatedAt(), "");
            rows.add(new ProjectScenarioRow(
                    scenario.id(),
                    scenario.featureId(),
                    featureName,
                    safe(scenario.request().getScenarioName(), "-"),
                    safe(scenario.request().getScenarioTags(), ""),
                    runState,
                    message,
                    updatedAt
            ));
        }
        return rows;
    }

    private List<ProjectExecutionRow> projectExecutionRows(String projectId, String stateFilter) {
        return projectExecutionRows(projectSnapshot(projectId), stateFilter);
    }

    private List<ProjectExecutionRow> projectExecutionRows(ProjectSnapshot snapshot, String stateFilter) {
        List<ProjectExecutionRow> rows = new ArrayList<>();
        String normalizedFilter = stateFilter == null ? "" : stateFilter.trim().toUpperCase();
        for (ScenarioStore.ScenarioEntry scenario : snapshot.scenarios()) {
            FeatureStore.FeatureEntry feature = snapshot.featureById().get(scenario.featureId());
            String featureName = feature == null ? "-" : safe(feature.request().getFeatureName(), "-");
            ScenarioStore.RunStatus status = snapshot.statuses().get(scenario.id());
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
                    scenario.featureId(),
                    featureName,
                    safe(scenario.request().getScenarioName(), "-"),
                    runState,
                    safe(status.message(), "-"),
                    safe(status.updatedAt(), "")
            ));
        }
        return rows;
    }

    private Map<String, ProjectStore.ProjectEntry> projectById(List<ProjectStore.ProjectEntry> projects) {
        Map<String, ProjectStore.ProjectEntry> projectById = new HashMap<>();
        for (ProjectStore.ProjectEntry project : projects) {
            projectById.put(project.id(), project);
        }
        return projectById;
    }

    private Map<String, FeatureStore.FeatureEntry> featureById(List<FeatureStore.FeatureEntry> features) {
        Map<String, FeatureStore.FeatureEntry> featureById = new HashMap<>();
        for (FeatureStore.FeatureEntry feature : features) {
            featureById.put(feature.id(), feature);
        }
        return featureById;
    }

    private Map<String, List<FeatureStore.FeatureEntry>> featuresByProject(List<FeatureStore.FeatureEntry> features) {
        Map<String, List<FeatureStore.FeatureEntry>> featuresByProject = new HashMap<>();
        for (FeatureStore.FeatureEntry feature : features) {
            featuresByProject.computeIfAbsent(feature.projectId(), ignored -> new ArrayList<>()).add(feature);
        }
        return featuresByProject;
    }

    private Map<String, List<ScenarioStore.ScenarioEntry>> scenariosByFeature(List<ScenarioStore.ScenarioEntry> scenarios) {
        Map<String, List<ScenarioStore.ScenarioEntry>> scenariosByFeature = new HashMap<>();
        for (ScenarioStore.ScenarioEntry scenario : scenarios) {
            scenariosByFeature.computeIfAbsent(scenario.featureId(), ignored -> new ArrayList<>()).add(scenario);
        }
        return scenariosByFeature;
    }

    private ProjectSnapshot projectSnapshot(String projectId) {
        List<FeatureStore.FeatureEntry> features = FeatureStore.listByProject(projectId);
        List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.listByProject(projectId);
        return new ProjectSnapshot(
                features,
                scenarios,
                featureById(features),
                scenariosByFeature(scenarios),
                ScenarioStore.listRunStatuses()
        );
    }

    private ExecutionSummary buildExecutionSummary(List<ProjectExecutionRow> sourceRows, int limit) {
        Map<String, List<ProjectExecutionRow>> byFeature = new HashMap<>();
        for (ProjectExecutionRow row : sourceRows) {
            byFeature.computeIfAbsent(row.featureId(), id -> new ArrayList<>()).add(row);
        }

        List<FeatureExecutionRow> allFeatures = new ArrayList<>();
        List<FeatureExecutionDetail> details = new ArrayList<>();
        for (Map.Entry<String, List<ProjectExecutionRow>> entry : byFeature.entrySet()) {
            String featureId = entry.getKey();
            List<ProjectExecutionRow> rows = entry.getValue();
            rows.sort((a, b) -> parseRunStamp(b.updatedAt()).compareTo(parseRunStamp(a.updatedAt())));
            if (rows.isEmpty()) {
                continue;
            }
            int successCount = 0;
            int failureCount = 0;
            int runningCount = 0;
            List<FeatureScenarioExecutionRow> scenarios = new ArrayList<>();
            for (ProjectExecutionRow row : rows) {
                String state = normalizeState(row.state());
                if ("SUCCESS".equals(state)) {
                    successCount++;
                } else if ("FAILURE".equals(state)) {
                    failureCount++;
                } else if ("RUNNING".equals(state)) {
                    runningCount++;
                }
                scenarios.add(new FeatureScenarioExecutionRow(
                        row.scenarioId(),
                        row.scenarioName(),
                        state,
                        safe(row.message(), "-"),
                        safe(row.updatedAt(), "")
                ));
            }
            FeatureExecutionRow latestFeatureRow = new FeatureExecutionRow(
                    featureId,
                    rows.get(0).featureName(),
                    aggregateFeatureExecutionState(successCount, failureCount, runningCount),
                    successCount,
                    failureCount,
                    runningCount,
                    rows.size(),
                    safe(rows.get(0).message(), "-"),
                    safe(rows.get(0).updatedAt(), "")
            );
            allFeatures.add(latestFeatureRow);
            details.add(new FeatureExecutionDetail(featureId, rows.get(0).featureName(), scenarios));
        }

        allFeatures.sort((a, b) -> parseRunStamp(b.updatedAt()).compareTo(parseRunStamp(a.updatedAt())));
        details.sort((a, b) -> parseRunStamp(latestDetailStamp(b)).compareTo(parseRunStamp(latestDetailStamp(a))));
        int window = Math.min(Math.max(1, limit), allFeatures.size());
        List<FeatureExecutionRow> latest = window == 0
                ? List.of()
                : new ArrayList<>(allFeatures.subList(0, window));

        int success = 0;
        int failure = 0;
        int running = 0;
        int notRun = 0;
        for (FeatureExecutionRow row : latest) {
            String state = normalizeState(row.state());
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

        List<FeatureExecutionRow> chronological = new ArrayList<>(latest);
        Collections.reverse(chronological);
        List<ExecutionTrendPoint> trend = new ArrayList<>();
        int cumulativeSuccess = 0;
        int cumulativeFailure = 0;
        int index = 1;
        for (FeatureExecutionRow row : chronological) {
            String state = normalizeState(row.state());
            int pointSuccess = "SUCCESS".equals(state) ? 1 : 0;
            int pointFailure = "FAILURE".equals(state) ? 1 : 0;
            int pointRunning = "RUNNING".equals(state) ? 1 : 0;
            int pointNotRun = (!"SUCCESS".equals(state) && !"FAILURE".equals(state) && !"RUNNING".equals(state)) ? 1 : 0;
            cumulativeSuccess += pointSuccess;
            cumulativeFailure += pointFailure;
            int done = cumulativeSuccess + cumulativeFailure;
            int successRate = done == 0 ? 0 : (int) Math.round((cumulativeSuccess * 100.0) / done);
            String label = shortStamp(row.updatedAt(), index);
            trend.add(new ExecutionTrendPoint(label, pointSuccess, pointFailure, pointRunning, pointNotRun, successRate));
            index++;
        }

        List<String> latestFeatureIds = new ArrayList<>();
        for (FeatureExecutionRow row : latest) {
            latestFeatureIds.add(row.featureId());
        }
        List<FeatureExecutionDetail> latestDetails = new ArrayList<>();
        for (FeatureExecutionDetail detail : details) {
            if (latestFeatureIds.contains(detail.featureId())) {
                latestDetails.add(detail);
            }
        }

        return new ExecutionSummary(
                trend,
                new ExecutionTotals(success, failure, running, notRun),
                latest,
                latestDetails,
                allFeatures.size(),
                window
        );
    }

    private String normalizeState(String value) {
        if (value == null || value.isBlank()) {
            return "NOT_RUN";
        }
        return value.trim().toUpperCase();
    }

    private LocalDateTime parseRunStamp(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.MIN;
        }
        try {
            return LocalDateTime.parse(value.trim(), RUN_STAMP);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.MIN;
        }
    }

    private String shortStamp(String value, int fallbackIndex) {
        LocalDateTime parsed = parseRunStamp(value);
        if (LocalDateTime.MIN.equals(parsed)) {
            return "Run " + fallbackIndex;
        }
        return parsed.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    private String aggregateFeatureExecutionState(int successCount, int failureCount, int runningCount) {
        if (runningCount > 0) {
            return "RUNNING";
        }
        if (failureCount > 0) {
            return "FAILURE";
        }
        if (successCount > 0) {
            return "SUCCESS";
        }
        return "NOT_RUN";
    }

    private String latestDetailStamp(FeatureExecutionDetail detail) {
        if (detail == null || detail.scenarios() == null || detail.scenarios().isEmpty()) {
            return "";
        }
        return safe(detail.scenarios().get(0).updatedAt(), "");
    }

    private FeatureStore.FeatureEntry findFeature(String projectId, String featureId) {
        String normalizedProjectId = normalizePathId(projectId);
        String normalizedFeatureId = normalizePathId(featureId);
        FeatureStore.FeatureEntry feature = FeatureStore.findById(normalizedFeatureId)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + featureId));
        if (!feature.projectId().equals(normalizedProjectId)) {
            throw new IllegalArgumentException("Feature does not belong to project: " + projectId);
        }
        return feature;
    }

    private String normalizePathId(String id) {
        if (id == null) {
            return "";
        }
        String value = id.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
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

    private void regenerateProjectArtifacts(ProjectStore.ProjectEntry project) {
        for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(project.id())) {
            List<ScenarioStore.ScenarioEntry> scenarios = ScenarioStore.listByFeature(feature.id());
            if (scenarios.isEmpty()) {
                continue;
            }
            try {
                regenerateFeatureArtifacts(project, feature, scenarios.get(0));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to regenerate feature files for project update.", e);
            }
        }
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

    public record BatchRunResult(String id, String scenarioName, String engine, int exitCode, String summary) {
    }

    public record ScenarioRow(String id, ScenarioRequest request, ScenarioStore.RunStatus status) {
    }

    public record FeatureCard(String id, FeatureRequest request, List<ScenarioRow> scenarios, String status) {
    }

    public record ScenarioStatusView(String id, String state, String message, String updatedAt) {
    }

    public record ProjectMetrics(int features,
                                 int featuresWithScenarios,
                                 int featuresWithoutScenarios,
                                 int scenarios,
                                 int success,
                                 int failure,
                                 int running,
                                 int notRun) {
    }

    public record ProjectDashboardRow(String id, ProjectRequest project, ProjectMetrics metrics) {
    }

    public record RunningTestRow(String scenarioId,
                                 String projectId,
                                 String projectName,
                                 String featureId,
                                 String featureName,
                                 String scenarioName,
                                 String state,
                                 String message,
                                 String updatedAt) {
    }

    public record FeatureOverviewRow(String featureId,
                                     String projectId,
                                     String projectName,
                                     String featureName,
                                     String description,
                                     String baseUrl,
                                     String jiraStories,
                                     int scenarioCount) {
    }

    public record ScenarioOverviewRow(String scenarioId,
                                      String projectId,
                                      String projectName,
                                      String featureId,
                                      String featureName,
                                      String scenarioName,
                                      String tags,
                                      String state,
                                      String message,
                                      String updatedAt) {
    }

    public record ProjectScenarioRow(String scenarioId,
                                     String featureId,
                                     String featureName,
                                     String scenarioName,
                                     String tags,
                                     String state,
                                     String message,
                                     String updatedAt) {
    }

    public record ProjectExecutionRow(String scenarioId,
                                      String featureId,
                                      String featureName,
                                      String scenarioName,
                                      String state,
                                      String message,
                                      String updatedAt) {
    }

    public record ExecutionTrendPoint(String label,
                                      int success,
                                      int failure,
                                      int running,
                                      int notRun,
                                      int successRate) {
    }

    public record ExecutionTotals(int success, int failure, int running, int notRun) {
    }

    public record FeatureScenarioExecutionRow(String scenarioId,
                                              String scenarioName,
                                              String state,
                                              String message,
                                              String updatedAt) {
    }

    public record FeatureExecutionRow(String featureId,
                                      String featureName,
                                      String state,
                                      int successCount,
                                      int failureCount,
                                      int runningCount,
                                      int scenarioCount,
                                      String message,
                                      String updatedAt) {
    }

    public record FeatureExecutionDetail(String featureId,
                                         String featureName,
                                         List<FeatureScenarioExecutionRow> scenarios) {
    }

    public record ExecutionSummary(List<ExecutionTrendPoint> trend,
                                   ExecutionTotals totals,
                                   List<FeatureExecutionRow> latestRows,
                                   List<FeatureExecutionDetail> details,
                                   int totalRows,
                                   int windowSize) {
    }

    private record ProjectSnapshot(List<FeatureStore.FeatureEntry> features,
                                   List<ScenarioStore.ScenarioEntry> scenarios,
                                   Map<String, FeatureStore.FeatureEntry> featureById,
                                   Map<String, List<ScenarioStore.ScenarioEntry>> scenariosByFeature,
                                   Map<String, ScenarioStore.RunStatus> statuses) {
    }

    public record AiScenarioGenerationResponse(String scenarioSteps,
                                               String provider,
                                               List<String> assumptions,
                                               String stepDefinitionsPreview,
                                               String pageObjectPreview) {
    }

}
