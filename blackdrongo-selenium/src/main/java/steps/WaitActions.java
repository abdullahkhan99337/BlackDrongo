package steps;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;
import java.util.List;

public class WaitActions {

    private final WebDriver driver;

    public WaitActions(WebDriver driver) {
        this.driver = driver;
    }

    public FluentWait<WebDriver> waitFor(long timeoutSeconds, long pollingMillis) {
        return new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(timeoutSeconds))
                .pollingEvery(Duration.ofMillis(pollingMillis))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    public WebElement untilPresent(By locator, long timeoutSeconds, long pollingMillis) {
        return waitFor(timeoutSeconds, pollingMillis).until(d -> d.findElement(locator));
    }

    public WebElement untilVisible(By locator, long timeoutSeconds, long pollingMillis) {
        return waitFor(timeoutSeconds, pollingMillis).until(d -> {
            WebElement element = d.findElement(locator);
            return element.isDisplayed() ? element : null;
        });
    }

    public WebElement untilClickable(By locator, long timeoutSeconds, long pollingMillis) {
        return waitFor(timeoutSeconds, pollingMillis).until(d -> {
            WebElement element = d.findElement(locator);
            return (element.isDisplayed() && element.isEnabled()) ? element : null;
        });
    }

    public boolean untilInvisible(By locator, long timeoutSeconds, long pollingMillis) {
        return waitFor(timeoutSeconds, pollingMillis).until(d -> {
            List<WebElement> elements = d.findElements(locator);
            if (elements.isEmpty()) {
                return true;
            }
            return !elements.get(0).isDisplayed();
        });
    }

    public boolean untilTextContains(By locator, String expectedText, long timeoutSeconds, long pollingMillis) {
        return waitFor(timeoutSeconds, pollingMillis).until(d -> {
            WebElement element = d.findElement(locator);
            String actual = element.getText();
            return actual != null && actual.contains(expectedText);
        });
    }
}
