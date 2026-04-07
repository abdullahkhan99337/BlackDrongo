package com.blackdrongo.uigen.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class BuildController {
    private final BuildExecutionService buildExecutionService;

    public BuildController(BuildExecutionService buildExecutionService) {
        this.buildExecutionService = buildExecutionService;
    }

    @GetMapping("/builds/new")
    public String newBuild(Model model) {
        populateBuildModel(model, new BuildLaunchRequest());
        return "build-launcher";
    }

    @GetMapping("/builds/history")
    public String buildHistory(Model model) {
        model.addAttribute("recentBuilds", buildExecutionService.recentRuns());
        model.addAttribute("buildSchedules", buildExecutionService.schedules());
        return "build-history";
    }

    @PostMapping("/builds/launch")
    public String launchBuild(@ModelAttribute("buildRequest") BuildLaunchRequest buildRequest, Model model) {
        try {
            BuildExecutionService.SubmitResult result = buildExecutionService.submit(buildRequest);
            return "redirect:/builds/new?banner=" + (result.scheduled() ? "scheduled" : "queued");
        } catch (IllegalArgumentException e) {
            populateBuildModel(model, buildRequest);
            model.addAttribute("buildError", e.getMessage());
            return "build-launcher";
        }
    }

    @PostMapping("/builds/schedules/{scheduleId}/cancel")
    public String cancelSchedule(@PathVariable("scheduleId") String scheduleId) {
        buildExecutionService.cancelSchedule(scheduleId);
        return "redirect:/builds/new?banner=schedule_cancelled";
    }

    private void populateBuildModel(Model model, BuildLaunchRequest request) {
        model.addAttribute("buildRequest", request);
        model.addAttribute("buildProjects", buildProjects());
        model.addAttribute("recentBuilds", buildExecutionService.recentRuns());
        model.addAttribute("buildSchedules", buildExecutionService.schedules());
    }

    private List<BuildProjectView> buildProjects() {
        List<BuildProjectView> rows = new ArrayList<>();
        for (ProjectStore.ProjectEntry project : ProjectStore.list()) {
            List<BuildFeatureView> features = new ArrayList<>();
            for (FeatureStore.FeatureEntry feature : FeatureStore.listByProject(project.id())) {
                List<BuildScenarioView> scenarios = new ArrayList<>();
                for (ScenarioStore.ScenarioEntry scenario : ScenarioStore.listByFeature(feature.id())) {
                    scenarios.add(new BuildScenarioView(
                            scenario.id(),
                            safe(scenario.request().getScenarioName()),
                            safe(scenario.request().getScenarioTags())
                    ));
                }
                scenarios.sort(Comparator.comparing(BuildScenarioView::name, String.CASE_INSENSITIVE_ORDER));
                features.add(new BuildFeatureView(
                        feature.id(),
                        safe(feature.request().getFeatureName()),
                        safe(feature.request().getBaseUrl()),
                        scenarios
                ));
            }
            features.sort(Comparator.comparing(BuildFeatureView::name, String.CASE_INSENSITIVE_ORDER));
            rows.add(new BuildProjectView(
                    project.id(),
                    safe(project.project().getProjectName()),
                    safe(project.project().getEngine()),
                    safe(project.project().getBrowser()),
                    features
            ));
        }
        rows.sort(Comparator.comparing(BuildProjectView::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record BuildProjectView(String id,
                                   String name,
                                   String engine,
                                   String browser,
                                   List<BuildFeatureView> features) {
    }

    public record BuildFeatureView(String id,
                                   String name,
                                   String baseUrl,
                                   List<BuildScenarioView> scenarios) {
    }

    public record BuildScenarioView(String id,
                                    String name,
                                    String tags) {
    }
}
