package com.blackdrongo.uigen.web;

public class ProjectRequest {
    private String projectName;
    private String engine;
    private String browser;
    private boolean headless;
    private String baseUrl;
    private boolean chromeStartMaximized = true;
    private boolean chromeIncognito = true;
    private boolean chromeDisableNotifications = true;
    private boolean chromeDisablePopupBlocking = true;
    private boolean chromeAcceptInsecureCerts = true;
    private String chromeCustomArgs = "";
    private boolean firefoxPrivateMode = true;
    private boolean firefoxAcceptInsecureCerts = true;
    private String firefoxCustomArgs = "";
    private boolean edgeStartMaximized = true;
    private boolean edgeInPrivate = true;
    private boolean edgeAcceptInsecureCerts = true;
    private String edgeCustomArgs = "";

    public ProjectRequest copy() {
        ProjectRequest copy = new ProjectRequest();
        copy.projectName = this.projectName;
        copy.engine = this.engine;
        copy.browser = this.browser;
        copy.headless = this.headless;
        copy.baseUrl = this.baseUrl;
        copy.chromeStartMaximized = this.chromeStartMaximized;
        copy.chromeIncognito = this.chromeIncognito;
        copy.chromeDisableNotifications = this.chromeDisableNotifications;
        copy.chromeDisablePopupBlocking = this.chromeDisablePopupBlocking;
        copy.chromeAcceptInsecureCerts = this.chromeAcceptInsecureCerts;
        copy.chromeCustomArgs = this.chromeCustomArgs;
        copy.firefoxPrivateMode = this.firefoxPrivateMode;
        copy.firefoxAcceptInsecureCerts = this.firefoxAcceptInsecureCerts;
        copy.firefoxCustomArgs = this.firefoxCustomArgs;
        copy.edgeStartMaximized = this.edgeStartMaximized;
        copy.edgeInPrivate = this.edgeInPrivate;
        copy.edgeAcceptInsecureCerts = this.edgeAcceptInsecureCerts;
        copy.edgeCustomArgs = this.edgeCustomArgs;
        return copy;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isChromeStartMaximized() {
        return chromeStartMaximized;
    }

    public void setChromeStartMaximized(boolean chromeStartMaximized) {
        this.chromeStartMaximized = chromeStartMaximized;
    }

    public boolean isChromeIncognito() {
        return chromeIncognito;
    }

    public void setChromeIncognito(boolean chromeIncognito) {
        this.chromeIncognito = chromeIncognito;
    }

    public boolean isChromeDisableNotifications() {
        return chromeDisableNotifications;
    }

    public void setChromeDisableNotifications(boolean chromeDisableNotifications) {
        this.chromeDisableNotifications = chromeDisableNotifications;
    }

    public boolean isChromeDisablePopupBlocking() {
        return chromeDisablePopupBlocking;
    }

    public void setChromeDisablePopupBlocking(boolean chromeDisablePopupBlocking) {
        this.chromeDisablePopupBlocking = chromeDisablePopupBlocking;
    }

    public boolean isChromeAcceptInsecureCerts() {
        return chromeAcceptInsecureCerts;
    }

    public void setChromeAcceptInsecureCerts(boolean chromeAcceptInsecureCerts) {
        this.chromeAcceptInsecureCerts = chromeAcceptInsecureCerts;
    }

    public String getChromeCustomArgs() {
        return chromeCustomArgs;
    }

    public void setChromeCustomArgs(String chromeCustomArgs) {
        this.chromeCustomArgs = chromeCustomArgs;
    }

    public boolean isFirefoxPrivateMode() {
        return firefoxPrivateMode;
    }

    public void setFirefoxPrivateMode(boolean firefoxPrivateMode) {
        this.firefoxPrivateMode = firefoxPrivateMode;
    }

    public boolean isFirefoxAcceptInsecureCerts() {
        return firefoxAcceptInsecureCerts;
    }

    public void setFirefoxAcceptInsecureCerts(boolean firefoxAcceptInsecureCerts) {
        this.firefoxAcceptInsecureCerts = firefoxAcceptInsecureCerts;
    }

    public String getFirefoxCustomArgs() {
        return firefoxCustomArgs;
    }

    public void setFirefoxCustomArgs(String firefoxCustomArgs) {
        this.firefoxCustomArgs = firefoxCustomArgs;
    }

    public boolean isEdgeStartMaximized() {
        return edgeStartMaximized;
    }

    public void setEdgeStartMaximized(boolean edgeStartMaximized) {
        this.edgeStartMaximized = edgeStartMaximized;
    }

    public boolean isEdgeInPrivate() {
        return edgeInPrivate;
    }

    public void setEdgeInPrivate(boolean edgeInPrivate) {
        this.edgeInPrivate = edgeInPrivate;
    }

    public boolean isEdgeAcceptInsecureCerts() {
        return edgeAcceptInsecureCerts;
    }

    public void setEdgeAcceptInsecureCerts(boolean edgeAcceptInsecureCerts) {
        this.edgeAcceptInsecureCerts = edgeAcceptInsecureCerts;
    }

    public String getEdgeCustomArgs() {
        return edgeCustomArgs;
    }

    public void setEdgeCustomArgs(String edgeCustomArgs) {
        this.edgeCustomArgs = edgeCustomArgs;
    }
}
