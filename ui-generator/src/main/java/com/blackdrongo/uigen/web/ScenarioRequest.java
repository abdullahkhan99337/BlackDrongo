package com.blackdrongo.uigen.web;

public class ScenarioRequest {
    private String scenarioName;
    private String baseUrl;
    private String scenarioSteps;
    private String scenarioTags;
    private String mvnArgs;
    private String scenarioId;

    public ScenarioRequest copy() {
        ScenarioRequest copy = new ScenarioRequest();
        copy.scenarioName = this.scenarioName;
        copy.baseUrl = this.baseUrl;
        copy.scenarioSteps = this.scenarioSteps;
        copy.scenarioTags = this.scenarioTags;
        copy.mvnArgs = this.mvnArgs;
        copy.scenarioId = this.scenarioId;
        return copy;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getScenarioSteps() {
        return scenarioSteps;
    }

    public void setScenarioSteps(String scenarioSteps) {
        this.scenarioSteps = scenarioSteps;
    }

    public String getScenarioTags() {
        return scenarioTags;
    }

    public void setScenarioTags(String scenarioTags) {
        this.scenarioTags = scenarioTags;
    }

    public String getMvnArgs() {
        return mvnArgs;
    }

    public void setMvnArgs(String mvnArgs) {
        this.mvnArgs = mvnArgs;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }
}
