package com.blackdrongo.uigen.web;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TestRunService {
    private static final Map<String, TestRunStatus> RUNS = new ConcurrentHashMap<>();

    public static String start(Path projectRoot, String tags, String mvnArgs) {
        return start(projectRoot, tags, mvnArgs, null);
    }

    public static String start(Path projectRoot, String tags, String mvnArgs, String scenarioId) {
        return start(projectRoot, tags, mvnArgs, null, null, scenarioId);
    }

    public static String start(Path projectRoot, String tags, String mvnArgs,
                               String scenarioName, Path featuresPath, String scenarioId) {
        String id = UUID.randomUUID().toString();
        String startMessage = "Running tests...";
        RUNS.put(id, new TestRunStatus(id, "RUNNING", startMessage, "", 0));
        if (scenarioId != null) {
            ScenarioStore.updateRunStatus(scenarioId, "RUNNING", startMessage);
        }
        new Thread(() -> {
            TestRunResult result = TestRunner.run(projectRoot, tags, mvnArgs, scenarioName, featuresPath);
            String state = result.exitCode() == 0 ? "SUCCESS" : "FAILURE";
            String message = result.summary();
            RUNS.put(id, new TestRunStatus(id, state, message, result.output(), result.exitCode()));
            if (scenarioId != null) {
                ScenarioStore.updateRunStatus(scenarioId, state, message);
            }
        }, "test-run-" + id).start();
        return id;
    }

    public static TestRunStatus get(String id) {
        return RUNS.get(id);
    }

    public record TestRunStatus(String id, String state, String message, String output, int exitCode) {}
}
