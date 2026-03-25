package browsers;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;


public class BrowserManager {


    private static volatile BrowserManager browser = null;
    private static ThreadLocal<WebDriver> tlDriver =  new ThreadLocal<>();

    private BrowserManager(){}

    public static BrowserManager getInstance(){
        if (browser == null){
            synchronized (BrowserManager.class){
                if (browser == null){
                    browser = new BrowserManager();
                }
            }
        }

        if(tlDriver.get() == null){
            ConfigReader config = ConfigReader.getInstance();
            browser.initDriver(config.getProperty("browser"));
            String maximize = config.getOptionalProperty("window.maximize");
            if (maximize == null || Boolean.parseBoolean(maximize)) {
                browser.maximizeBrowser();
            }
        }
        return browser;
    }

    public WebDriver getDriver(){
        return tlDriver.get();
    }

    private WebDriver getDriverOrThrow() {
        WebDriver driver = tlDriver.get();
        if (driver == null) {
            throw new IllegalStateException("WebDriver is not initialized. Call BrowserManager.getInstance() first.");
        }
        return driver;
    }

    public BrowserManager maximizeBrowser(){
        getDriverOrThrow().manage().window().maximize();
        return this;
    }

    public BrowserManager fullScreenBrowser() {
        getDriverOrThrow().manage().window().fullscreen();
        return this;
    }

    public void closeBrowser(){
        WebDriver driver = getDriverOrThrow();
        if (driver != null) {
            driver.close();
        }
    }

    public BrowserManager quitBrowser(){
        WebDriver driver = getDriverOrThrow();
        if (driver != null) {
            driver.quit();
            tlDriver.remove();
        }
        return this;
    }

    public BrowserManager open(String url){
        getDriverOrThrow().get(url);
        return this;
    }

    public BrowserManager navigateBack() {
        getDriverOrThrow().navigate().back();
        return this;
    }

    public BrowserManager navigateForward() {
        getDriverOrThrow().navigate().forward();
        return this;
    }

    public BrowserManager refreshPage() {
        getDriverOrThrow().navigate().refresh();
        return this;
    }

    public String getCurrentUrl() {
        return getDriverOrThrow().getCurrentUrl();
    }

    public String getTitle() {
        return getDriverOrThrow().getTitle();
    }

    public BrowserManager switchToDefaultContent() {
        getDriverOrThrow().switchTo().defaultContent();
        return this;
    }

    public BrowserManager switchToParentFrame() {
        getDriverOrThrow().switchTo().parentFrame();
        return this;
    }

    public BrowserManager switchToFrame(int index) {
        getDriverOrThrow().switchTo().frame(index);
        return this;
    }

    public BrowserManager switchToFrame(String nameOrId) {
        getDriverOrThrow().switchTo().frame(nameOrId);
        return this;
    }

    public BrowserManager switchToFrame(By locator) {
        WebElement frameElement = getDriverOrThrow().findElement(locator);
        getDriverOrThrow().switchTo().frame(frameElement);
        return this;
    }

    public BrowserManager openNewTab() {
        getDriverOrThrow().switchTo().newWindow(WindowType.TAB);
        return this;
    }

    public BrowserManager openNewWindow() {
        getDriverOrThrow().switchTo().newWindow(WindowType.WINDOW);
        return this;
    }

    public BrowserManager switchToWindowByIndex(int index) {
        List<String> handles = new ArrayList<>(getDriverOrThrow().getWindowHandles());
        if (index < 0 || index >= handles.size()) {
            throw new IllegalArgumentException("Invalid window index: " + index + ". Available windows: " + handles.size());
        }
        getDriverOrThrow().switchTo().window(handles.get(index));
        return this;
    }

    public BrowserManager switchToWindowByTitleContains(String partialTitle) {
        for (String handle : getDriverOrThrow().getWindowHandles()) {
            getDriverOrThrow().switchTo().window(handle);
            String title = getDriverOrThrow().getTitle();
            if (title != null && title.contains(partialTitle)) {
                return this;
            }
        }
        throw new IllegalStateException("No window title contains: " + partialTitle);
    }

    public BrowserManager acceptAlert() {
        getDriverOrThrow().switchTo().alert().accept();
        return this;
    }

    public BrowserManager dismissAlert() {
        getDriverOrThrow().switchTo().alert().dismiss();
        return this;
    }

    public BrowserManager sendKeysToAlert(String value) {
        Alert alert = getDriverOrThrow().switchTo().alert();
        alert.sendKeys(value);
        return this;
    }

    public String getAlertText() {
        return getDriverOrThrow().switchTo().alert().getText();
    }

    public BrowserManager verifyAlertText(String expectedText) {
        String actualText = getAlertText();
        if (!expectedText.equals(actualText)) {
            throw new AssertionError("Expected alert text: " + expectedText + " but Found: " + actualText);
        }
        return this;
    }

    public BrowserManager addCookie(String name, String value) {
        getDriverOrThrow().manage().addCookie(new Cookie(name, value));
        return this;
    }

    public BrowserManager deleteCookie(String name) {
        getDriverOrThrow().manage().deleteCookieNamed(name);
        return this;
    }

    public BrowserManager deleteAllCookies() {
        getDriverOrThrow().manage().deleteAllCookies();
        return this;
    }

    public boolean hasCookie(String name) {
        return getDriverOrThrow().manage().getCookieNamed(name) != null;
    }

    public Cookie getCookie(String name) {
        return getDriverOrThrow().manage().getCookieNamed(name);
    }

    public BrowserManager verifyCurrentUrl(String expectedUrl) {
        String actualUrl = getCurrentUrl();
        if (!expectedUrl.equals(actualUrl)) {
            throw new AssertionError("Expected URL: " + expectedUrl + " but Found: " + actualUrl);
        }
        return this;
    }

    public BrowserManager verifyCurrentUrlContains(String partialUrl) {
        String actualUrl = getCurrentUrl();
        if (!actualUrl.contains(partialUrl)) {
            throw new AssertionError("Expected URL containing: " + partialUrl + " but Found: " + actualUrl);
        }
        return this;
    }

    public BrowserManager verifyTitle(String expectedTitle) {
        String actualTitle = getTitle();
        if (!expectedTitle.equals(actualTitle)) {
            throw new AssertionError("Expected title: " + expectedTitle + " but Found: " + actualTitle);
        }
        return this;
    }

    public BrowserManager verifyTitleContains(String partialTitle) {
        String actualTitle = getTitle();
        if (!actualTitle.contains(partialTitle)) {
            throw new AssertionError("Expected title containing: " + partialTitle + " but Found: " + actualTitle);
        }
        return this;
    }

    public static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value, e);
        }
    }

    private void initDriver(String browserName){
        WebDriver driver = DriverFactory.getInstance(browserName);
        tlDriver.set(driver);
        configureDriver(driver);
    }

    private void configureDriver(WebDriver driver) {
        ConfigReader config = ConfigReader.getInstance();
        long pageLoadSeconds = parseLong(config.getOptionalProperty("timeout.pageLoadSeconds"), 60);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(Math.max(pageLoadSeconds, 0)));
    }


}
