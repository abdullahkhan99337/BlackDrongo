package steps;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

public class ElementAssertions {

    private final WebDriver driver;

    public ElementAssertions(WebDriver driver) {
        this.driver = driver;
    }

    public void textEquals(WebElement element, String expected) {
        String actual = element.getText();
        if (!actual.equals(expected)) {
            throw new AssertionError("Expected: " + expected + " but Found: " + actual);
        }
    }

    public void textContains(WebElement element, String expectedPart) {
        String actual = element.getText();
        if (!actual.contains(expectedPart)) {
            throw new AssertionError("Expected text containing: " + expectedPart + " but Found: " + actual);
        }
    }

    public void textEqualsIgnoreCase(WebElement element, String expected) {
        String actual = element.getText();
        if (!actual.equalsIgnoreCase(expected)) {
            throw new AssertionError("Expected (ignore case): " + expected + " but Found: " + actual);
        }
    }

    public void isDisplayed(WebElement element, String locatorText) {
        if (!element.isDisplayed()) {
            throw new AssertionError("Expected element to be displayed: " + locatorText);
        }
    }

    public void isEnabled(WebElement element, String locatorText) {
        if (!element.isEnabled()) {
            throw new AssertionError("Expected element to be enabled: " + locatorText);
        }
    }

    public void isDisabled(WebElement element, String locatorText) {
        if (element.isEnabled()) {
            throw new AssertionError("Expected element to be disabled: " + locatorText);
        }
    }

    public void isSelected(WebElement element, String locatorText) {
        if (!element.isSelected()) {
            throw new AssertionError("Expected element to be selected: " + locatorText);
        }
    }

    public void valueEquals(WebElement element, String expected) {
        String actual = element.getAttribute("value");
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected value: " + expected + " but Found: " + actual);
        }
    }

    public void placeholderEquals(WebElement element, String expected) {
        String actual = element.getAttribute("placeholder");
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected placeholder: " + expected + " but Found: " + actual);
        }
    }

    public void attributeEquals(WebElement element, String attributeName, String expected) {
        String actual = element.getAttribute(attributeName);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected attribute [" + attributeName + "]: " + expected + " but Found: " + actual);
        }
    }

    public void cssEquals(WebElement element, String cssName, String expected) {
        String actual = element.getCssValue(cssName);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected css [" + cssName + "]: " + expected + " but Found: " + actual);
        }
    }

    public void selectedOptionEquals(WebElement element, String expectedText) {
        String selected = new Select(element).getFirstSelectedOption().getText();
        if (!expectedText.equals(selected)) {
            throw new AssertionError("Expected selected option: " + expectedText + " but Found: " + selected);
        }
    }

    public void optionsCountEquals(WebElement element, int expectedCount) {
        int actualCount = new Select(element).getOptions().size();
        if (actualCount != expectedCount) {
            throw new AssertionError("Expected options count: " + expectedCount + " but Found: " + actualCount);
        }
    }

    public void titleEquals(String expectedTitle) {
        String actualTitle = driver.getTitle();
        if (!expectedTitle.equals(actualTitle)) {
            throw new AssertionError("Expected title: " + expectedTitle + " but Found: " + actualTitle);
        }
    }

    public void urlEquals(String expectedUrl) {
        String actualUrl = driver.getCurrentUrl();
        if (!expectedUrl.equals(actualUrl)) {
            throw new AssertionError("Expected url: " + expectedUrl + " but Found: " + actualUrl);
        }
    }

    public void urlContains(String expectedPart) {
        String currentUrl = driver.getCurrentUrl();
        if (!currentUrl.contains(expectedPart)) {
            throw new AssertionError("Expected URL containing: " + expectedPart + " but Found: " + currentUrl);
        }
    }

    public void elementCountEquals(List<WebElement> elements, int expectedCount, String locatorText) {
        int actualCount = elements.size();
        if (actualCount != expectedCount) {
            throw new AssertionError("Expected count: " + expectedCount + " but Found: " + actualCount + " for locator: " + locatorText);
        }
    }

    public void listContainsText(List<WebElement> elements, String expectedText, String locatorText) {
        boolean found = false;
        for (WebElement item : elements) {
            if (item.getText().contains(expectedText)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError("No element contains text: " + expectedText + " for locator: " + locatorText);
        }
    }
}
