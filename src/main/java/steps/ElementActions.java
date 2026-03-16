package steps;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ElementActions {

    private final WebDriver driver;

    public ElementActions(WebDriver driver) {
        this.driver = driver;
    }

    public void click(WebElement element) {
        element.click();
    }

    public void type(WebElement element, String text) {
        element.sendKeys(text);
    }

    public void clear(WebElement element) {
        element.clear();
    }

    public void submit(WebElement element) {
        element.submit();
    }

    public void hover(WebElement element) {
        new Actions(driver).moveToElement(element).perform();
    }

    public void doubleClick(WebElement element) {
        new Actions(driver).doubleClick(element).perform();
    }

    public void rightClick(WebElement element) {
        new Actions(driver).contextClick(element).perform();
    }

    public void clickAndHold(WebElement element) {
        new Actions(driver).clickAndHold(element).perform();
    }

    public void release(WebElement element) {
        new Actions(driver).release(element).perform();
    }

    public void dragAndDrop(WebElement source, WebElement target) {
        new Actions(driver).dragAndDrop(source, target).perform();
    }

    public void keyDown(WebElement element, String key) {
        new Actions(driver).keyDown(element, Keys.valueOf(key.toUpperCase())).perform();
    }

    public void keyUp(WebElement element, String key) {
        new Actions(driver).keyUp(element, Keys.valueOf(key.toUpperCase())).perform();
    }

    public void selectByVisibleText(WebElement element, String text) {
        new Select(element).selectByVisibleText(text);
    }

    public void selectByValue(WebElement element, String value) {
        new Select(element).selectByValue(value);
    }

    public void selectByIndex(WebElement element, int index) {
        new Select(element).selectByIndex(index);
    }

    public void deselectAll(WebElement element) {
        new Select(element).deselectAll();
    }

    public void deselectByVisibleText(WebElement element, String text) {
        new Select(element).deselectByVisibleText(text);
    }

    public void deselectByValue(WebElement element, String value) {
        new Select(element).deselectByValue(value);
    }

    public void deselectByIndex(WebElement element, int index) {
        new Select(element).deselectByIndex(index);
    }

    public void saveElementScreenshot(WebElement element, String filePath) throws IOException {
        File source = element.getScreenshotAs(OutputType.FILE);
        Files.copy(source.toPath(), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING);
    }

    public int countChildren(WebElement element, By childLocator) {
        return element.findElements(childLocator).size();
    }

    public void uploadFile(WebElement element, String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new IllegalArgumentException("absolutePath must be provided for file upload.");
        }
        File file = new File(absolutePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + absolutePath);
        }
        if (!element.isDisplayed() || !element.isEnabled()) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].style.display='block';" +
                    "arguments[0].style.visibility='visible';" +
                    "arguments[0].style.opacity='1';" +
                    "arguments[0].removeAttribute('hidden');",
                    element
            );
        }
        element.sendKeys(file.getAbsolutePath());
    }
}
