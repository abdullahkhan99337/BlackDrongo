package com.blackdrongo.uigen.web;

import java.util.ArrayList;
import java.util.List;

public class BuildLaunchRequest {
    private String buildName = "";
    private String projectId = "";
    private String targetType = "PROJECT";
    private List<String> featureIds = new ArrayList<>();
    private List<String> scenarioIds = new ArrayList<>();
    private String tags = "";
    private boolean parallel;
    private String headlessMode = "DEFAULT";
    private String mvnArgs = "";
    private String scheduleMode = "NOW";
    private String scheduledAt = "";

    public String getBuildName() {
        return buildName;
    }

    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public List<String> getFeatureIds() {
        return featureIds;
    }

    public void setFeatureIds(List<String> featureIds) {
        this.featureIds = featureIds == null ? new ArrayList<>() : new ArrayList<>(featureIds);
    }

    public List<String> getScenarioIds() {
        return scenarioIds;
    }

    public void setScenarioIds(List<String> scenarioIds) {
        this.scenarioIds = scenarioIds == null ? new ArrayList<>() : new ArrayList<>(scenarioIds);
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isParallel() {
        return parallel;
    }

    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    public String getHeadlessMode() {
        return headlessMode;
    }

    public void setHeadlessMode(String headlessMode) {
        this.headlessMode = headlessMode;
    }

    public String getMvnArgs() {
        return mvnArgs;
    }

    public void setMvnArgs(String mvnArgs) {
        this.mvnArgs = mvnArgs;
    }

    public String getScheduleMode() {
        return scheduleMode;
    }

    public void setScheduleMode(String scheduleMode) {
        this.scheduleMode = scheduleMode;
    }

    public String getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(String scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}
