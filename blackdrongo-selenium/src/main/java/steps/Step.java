package steps;

import browsers.ConfigReader;
import browsers.BrowserManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

public class Step {

    private final WebDriver driver;
    private final WaitActions waitActions;
    private final ElementActions elementActions;
    private final ElementAssertions elementAssertions;
    private By element;
    private By secondElement;
    private By elements;
    private long timeoutSeconds = 60;
    private long pollingMillis = 500;
    private boolean autoWaitEnabled = true;

    private Step(WebDriver driver) {
        this.driver = driver;
        this.waitActions = new WaitActions(driver);
        this.elementActions = new ElementActions(driver);
        this.elementAssertions = new ElementAssertions(driver);
    }

    public static Step create(WebDriver driver) {
        Step step = new Step(driver);
        ConfigReader config = ConfigReader.getInstance();
        step.setTimeoutSeconds(BrowserManager.parseLong(
                config.getOptionalProperty("timeout.explicitSeconds"), 60));
        step.setPollingMillis(BrowserManager.parseLong(
                config.getOptionalProperty("polling.millis"), 500));
        return step;
    }

    public Step setWebElement(By element) {
        this.element = element;
        return this;
    }

    public Step setSecondWebElement(By secondElement) {
        this.secondElement = secondElement;
        return this;
    }

    public Step setWebElements(By elements) {
        this.elements = elements;
        return this;
    }

    public Step setTimeoutSeconds(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be greater than 0.");
        }
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public Step setPollingMillis(long pollingMillis) {
        if (pollingMillis <= 0) {
            throw new IllegalArgumentException("pollingMillis must be greater than 0.");
        }
        this.pollingMillis = pollingMillis;
        return this;
    }

    public Step setAutoWaitEnabled(boolean autoWaitEnabled) {
        this.autoWaitEnabled = autoWaitEnabled;
        return this;
    }

    public Step click() {
        elementActions.click(currentElement(true));
        return this;
    }

    public Step type(String value) {
        elementActions.type(currentElement(false), value);
        return this;
    }

    public Step clear() {
        elementActions.clear(currentElement(false));
        return this;
    }

    public Step submit() {
        elementActions.submit(currentElement(true));
        return this;
    }

    public Step hover() {
        elementActions.hover(currentElement(true));
        return this;
    }

    public Step doubleClick() {
        elementActions.doubleClick(currentElement(true));
        return this;
    }

    public Step rightClick() {
        elementActions.rightClick(currentElement(true));
        return this;
    }

    public Step dragAndDropToSecondElement() {
        if (secondElement == null) {
            throw new IllegalStateException("Second locator must be set before dragAndDropToSecondElement.");
        }
        elementActions.dragAndDrop(currentElement(true), secondElementCurrent(false));
        return this;
    }

    public Step clickAll() {
        By locator = requiredElementsLocator();
        if (autoWaitEnabled) {
            waitActions.untilPresent(locator, timeoutSeconds, pollingMillis);
        }
        List<WebElement> found = driver.findElements(locator);
        for (WebElement item : found) {
            elementActions.click(item);
        }
        return this;
    }

    public Step clickAllLinksWithBackNavigation() {
        By locator = requiredElementsLocator();
        if (autoWaitEnabled) {
            waitActions.untilPresent(locator, timeoutSeconds, pollingMillis);
        }
        List<WebElement> links = driver.findElements(locator);
        for (int i = 0; i < links.size(); i++) {
            links = driver.findElements(locator);
            if (i >= links.size()) {
                break;
            }
            WebElement link = links.get(i);
            String beforeUrl = driver.getCurrentUrl();
            try {
                elementActions.click(link);
            } catch (Exception clickError) {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", link);
            }
            String afterUrl = driver.getCurrentUrl();
            if (afterUrl != null && !afterUrl.equals(beforeUrl)) {
                driver.navigate().back();
                if (autoWaitEnabled) {
                    waitActions.untilPresent(locator, timeoutSeconds, pollingMillis);
                }
            }
        }
        return this;
    }

    public Step selectByVisibleText(String value) {
        elementActions.selectByVisibleText(currentElement(false), value);
        return this;
    }

    public Step selectByValue(String value) {
        elementActions.selectByValue(currentElement(false), value);
        return this;
    }

    public Step selectByIndex(int optionIndex) {
        elementActions.selectByIndex(currentElement(false), optionIndex);
        return this;
    }

    public Step waitForPresence() {
        waitActions.untilPresent(requiredLocator(), timeoutSeconds, pollingMillis);
        return this;
    }

    public Step waitForVisible() {
        waitActions.untilVisible(requiredLocator(), timeoutSeconds, pollingMillis);
        return this;
    }

    public Step waitForClickable() {
        waitActions.untilClickable(requiredLocator(), timeoutSeconds, pollingMillis);
        return this;
    }

    public Step waitForInvisible() {
        waitActions.untilInvisible(requiredLocator(), timeoutSeconds, pollingMillis);
        return this;
    }

    public Step waitForTextContains(String expectedPart) {
        waitActions.untilTextContains(requiredLocator(), expectedPart, timeoutSeconds, pollingMillis);
        return this;
    }

    public Step verifyText(String expected) {
        elementAssertions.textEquals(currentElement(false), expected);
        return this;
    }

    public Step verifyTextContains(String expectedPart) {
        elementAssertions.textContains(currentElement(false), expectedPart);
        return this;
    }

    public Step verifyDisplayed() {
        elementAssertions.isDisplayed(currentElement(false), requiredLocator().toString());
        return this;
    }

    public Step verifyEnabled() {
        elementAssertions.isEnabled(currentElement(false), requiredLocator().toString());
        return this;
    }

    public Step verifyDisabled() {
        elementAssertions.isDisabled(currentElement(false), requiredLocator().toString());
        return this;
    }

    public Step verifySelected() {
        elementAssertions.isSelected(currentElement(false), requiredLocator().toString());
        return this;
    }

    public Step verifyValue(String expected) {
        elementAssertions.valueEquals(currentElement(false), expected);
        return this;
    }

    public Step verifyAttribute(String name, String expected) {
        elementAssertions.attributeEquals(currentElement(false), name, expected);
        return this;
    }

    public Step verifyCssValue(String cssName, String expected) {
        elementAssertions.cssEquals(currentElement(false), cssName, expected);
        return this;
    }

    public Step verifyElementCount(int expectedCount) {
        By locator = requiredLocator();
        elementAssertions.elementCountEquals(driver.findElements(locator), expectedCount, locator.toString());
        return this;
    }

    public Step verifyUrl(String expectedUrl) {
        elementAssertions.urlEquals(expectedUrl);
        return this;
    }

    public Step verifyUrlContains(String expectedPart) {
        elementAssertions.urlContains(expectedPart);
        return this;
    }

    public Step verifyTitle(String expectedTitle) {
        elementAssertions.titleEquals(expectedTitle);
        return this;
    }

    public Step uploadFile(String absolutePath) {
        WebElement elementToUse = autoWaitEnabled
                ? waitActions.untilPresent(requiredLocator(), timeoutSeconds, pollingMillis)
                : driver.findElement(requiredLocator());
        elementActions.uploadFile(elementToUse, absolutePath);
        return this;
    }

    public String text() {
        return currentElement(false).getText();
    }

    public String attribute(String name) {
        return currentElement(false).getAttribute(name);
    }

    private By requiredLocator() {
        if (element == null) {
            throw new IllegalStateException("Call setWebElement(...) before executing step actions.");
        }
        return element;
    }

    private WebElement currentElement(boolean clickable) {
        By locator = requiredLocator();
        if (!autoWaitEnabled) {
            return driver.findElement(locator);
        }
        return clickable
                ? waitActions.untilClickable(locator, timeoutSeconds, pollingMillis)
                : waitActions.untilVisible(locator, timeoutSeconds, pollingMillis);
    }

    private WebElement secondElementCurrent(boolean clickable) {
        if (secondElement == null) {
            throw new IllegalStateException("Call setSecondWebElement(...) before using second element actions.");
        }
        if (!autoWaitEnabled) {
            return driver.findElement(secondElement);
        }
        return clickable
                ? waitActions.untilClickable(secondElement, timeoutSeconds, pollingMillis)
                : waitActions.untilVisible(secondElement, timeoutSeconds, pollingMillis);
    }

    private By requiredElementsLocator() {
        if (elements == null) {
            throw new IllegalStateException("Call setWebElements(...) before executing bulk actions.");
        }
        return elements;
    }

}
