package com.blackdrongo.uigen.web;

public class FeatureRequest {
    private String featureName;
    private String featureDescription;
    private String baseUrl;
    private String jiraUserStories;
    private String featureId;

    public FeatureRequest copy() {
        FeatureRequest copy = new FeatureRequest();
        copy.featureName = this.featureName;
        copy.featureDescription = this.featureDescription;
        copy.baseUrl = this.baseUrl;
        copy.jiraUserStories = this.jiraUserStories;
        copy.featureId = this.featureId;
        return copy;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getFeatureDescription() {
        return featureDescription;
    }

    public void setFeatureDescription(String featureDescription) {
        this.featureDescription = featureDescription;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getJiraUserStories() {
        return jiraUserStories;
    }

    public void setJiraUserStories(String jiraUserStories) {
        this.jiraUserStories = jiraUserStories;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }
}
