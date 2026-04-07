package playwright.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import playwright.browser.ConfigReader;
import playwright.browser.PlaywrightManager;

import java.util.Locale;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class Step {

    private final Page page;
    private Locator element;
    private Locator secondElement;
    private Locator elements;
    private long timeoutMillis = 60_000L;
    private boolean autoWaitEnabled = true;

    private Step(Page page) {
        this.page = page;
    }

    public static Step create(Page page) {
        Step step = new Step(page);
        ConfigReader config = ConfigReader.getInstance();
        long timeoutSeconds = parseLong(config.getOptionalProperty("timeout.explicitSeconds"), 60L);
        step.setTimeoutMillis(Math.max(timeoutSeconds, 1L) * 1000L);
        return step;
    }

    public static Step create() {
        return create(PlaywrightManager.getInstance().getPage());
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value, e);
        }
    }

    public Step setWebElement(String locator) {
        this.element = resolveLocator(locator);
        return this;
    }

    public Step setWebElement(Locator locator) {
        this.element = locator;
        return this;
    }

    public Step setSecondWebElement(String locator) {
        this.secondElement = resolveLocator(locator);
        return this;
    }

    public Step setSecondWebElement(Locator locator) {
        this.secondElement = locator;
        return this;
    }

    public Step setWebElements(String locator) {
        this.elements = resolveLocator(locator);
        return this;
    }

    public Step setWebElements(Locator locator) {
        this.elements = locator;
        return this;
    }

    public Step setTimeoutMillis(long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be greater than 0.");
        }
        this.timeoutMillis = timeoutMillis;
        return this;
    }

    public Step setAutoWaitEnabled(boolean autoWaitEnabled) {
        this.autoWaitEnabled = autoWaitEnabled;
        return this;
    }

    public Step click() {
        if (autoWaitEnabled) {
            currentElement().click(new Locator.ClickOptions().setTimeout(timeoutMillis));
        } else {
            currentElement().click();
        }
        return this;
    }

    public Step type(String value) {
        if (autoWaitEnabled) {
            currentElement().fill(value, new Locator.FillOptions().setTimeout(timeoutMillis));
        } else {
            currentElement().fill(value);
        }
        return this;
    }

    public Step clear() {
        return type("");
    }

    public Step hover() {
        if (autoWaitEnabled) {
            currentElement().hover(new Locator.HoverOptions().setTimeout(timeoutMillis));
        } else {
            currentElement().hover();
        }
        return this;
    }

    public Step doubleClick() {
        if (autoWaitEnabled) {
            currentElement().dblclick(new Locator.DblclickOptions().setTimeout(timeoutMillis));
        } else {
            currentElement().dblclick();
        }
        return this;
    }

    public Step rightClick() {
        Locator.ClickOptions options = new Locator.ClickOptions().setButton(com.microsoft.playwright.options.MouseButton.RIGHT);
        if (autoWaitEnabled) {
            options.setTimeout(timeoutMillis);
        }
        currentElement().click(options);
        return this;
    }

    public Step dragAndDropToSecondElement() {
        Locator source = currentElement();
        Locator target = requiredSecondElement();
        if (autoWaitEnabled) {
            source.dragTo(target, new Locator.DragToOptions().setTimeout(timeoutMillis));
        } else {
            source.dragTo(target);
        }
        return this;
    }

    public Step clickAll() {
        Locator list = requiredElements();
        int count = list.count();
        for (int i = 0; i < count; i++) {
            Locator item = list.nth(i);
            if (autoWaitEnabled) {
                item.click(new Locator.ClickOptions().setTimeout(timeoutMillis));
            } else {
                item.click();
            }
        }
        return this;
    }

    public Step clickAllLinksWithBackNavigation() {
        Locator list = requiredElements();
        int count = list.count();
        for (int i = 0; i < count; i++) {
            String before = page.url();
            Locator item = list.nth(i);
            if (autoWaitEnabled) {
                item.click(new Locator.ClickOptions().setTimeout(timeoutMillis));
            } else {
                item.click();
            }
            String after = page.url();
            if (after != null && !after.equals(before)) {
                page.navigate(before);
            }
        }
        return this;
    }

    public Step selectByVisibleText(String value) {
        currentElement().selectOption(new SelectOption().setLabel(value));
        return this;
    }

    public Step selectByValue(String value) {
        currentElement().selectOption(value);
        return this;
    }

    public Step selectByIndex(int optionIndex) {
        currentElement().selectOption(new SelectOption().setIndex(optionIndex));
        return this;
    }

    public Step waitForVisible() {
        currentElement().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMillis));
        return this;
    }

    public Step waitForInvisible() {
        currentElement().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(timeoutMillis));
        return this;
    }

    public Step waitForPresence() {
        currentElement().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(timeoutMillis));
        return this;
    }

    public Step verifyText(String expected) {
        assertThat(currentElement()).hasText(expected);
        return this;
    }

    public Step verifyTextContains(String expectedPart) {
        assertThat(currentElement()).containsText(expectedPart);
        return this;
    }

    public Step verifyDisplayed() {
        assertThat(currentElement()).isVisible();
        return this;
    }

    public Step verifyEnabled() {
        assertThat(currentElement()).isEnabled();
        return this;
    }

    public Step verifyDisabled() {
        assertThat(currentElement()).isDisabled();
        return this;
    }

    public Step verifyElementCount(int expectedCount) {
        assertThat(requiredElements()).hasCount(expectedCount);
        return this;
    }

    public Step verifyUrl(String expectedUrl) {
        if (!expectedUrl.equals(page.url())) {
            throw new AssertionError("Expected URL: " + expectedUrl + " but found: " + page.url());
        }
        return this;
    }

    public Step verifyUrlContains(String expectedPart) {
        if (!page.url().contains(expectedPart)) {
            throw new AssertionError("Expected URL to contain: " + expectedPart + " but found: " + page.url());
        }
        return this;
    }

    public Step verifyTitle(String expectedTitle) {
        String actual = page.title();
        if (!expectedTitle.equals(actual)) {
            throw new AssertionError("Expected title: " + expectedTitle + " but found: " + actual);
        }
        return this;
    }

    public Step verifyTitleContains(String expectedPart) {
        String actual = page.title();
        if (actual == null || !actual.contains(expectedPart)) {
            throw new AssertionError("Expected title to contain: " + expectedPart + " but found: " + actual);
        }
        return this;
    }

    public Step uploadFile(String absolutePath) {
        currentElement().setInputFiles(java.nio.file.Paths.get(absolutePath));
        return this;
    }

    public String text() {
        return currentElement().innerText();
    }

    public String attribute(String name) {
        return currentElement().getAttribute(name);
    }

    private Locator currentElement() {
        if (element == null) {
            throw new IllegalStateException("Call setWebElement(...) before executing step actions.");
        }
        return element;
    }

    private Locator requiredSecondElement() {
        if (secondElement == null) {
            throw new IllegalStateException("Call setSecondWebElement(...) before using second element actions.");
        }
        return secondElement;
    }

    private Locator requiredElements() {
        if (elements == null) {
            throw new IllegalStateException("Call setWebElements(...) before executing bulk actions.");
        }
        return elements;
    }

    private Locator resolveLocator(String locator) {
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("Locator must not be blank.");
        }
        String value = locator.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        String prefix = lower.contains("=") ? lower.substring(0, lower.indexOf('=') + 1) : "";
        return switch (prefix) {
            case "xpath=", "css=", "text=" -> page.locator(value);
            case "id=" -> page.locator("#" + cssEscape(value.substring(3).trim()));
            case "name=" -> page.locator("[name=\"" + cssString(value.substring(5).trim()) + "\"]");
            case "label=" -> page.getByLabel(value.substring(6).trim()).first();
            case "role=" -> resolveRoleLocator(value);
            default -> {
                if (value.startsWith("//") || value.startsWith("(//")) {
                    yield page.locator("xpath=" + value);
                }
                if (value.startsWith("#") || value.startsWith(".") || value.startsWith("[") || value.contains(">")) {
                    yield page.locator(value);
                }
                yield page.getByText(value).first();
            }
        };
    }

    private Locator resolveRoleLocator(String value) {
        String[] parts = value.split("\\|");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid role locator: " + value);
        }

        String rolePart = parts[0].trim();
        if (!rolePart.toLowerCase(Locale.ROOT).startsWith("role=")) {
            throw new IllegalArgumentException("Role locator must start with role=: " + value);
        }

        String roleName = rolePart.substring(5).trim();
        if (roleName.isBlank()) {
            throw new IllegalArgumentException("Role name is required in locator: " + value);
        }

        AriaRole role = AriaRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        Page.GetByRoleOptions options = new Page.GetByRoleOptions();
        Integer index = null;

        for (int i = 1; i < parts.length; i++) {
            String option = parts[i].trim();
            if (option.isBlank()) {
                continue;
            }
            int split = option.indexOf('=');
            if (split <= 0 || split == option.length() - 1) {
                throw new IllegalArgumentException("Invalid role option: " + option);
            }
            String key = option.substring(0, split).trim().toLowerCase(Locale.ROOT);
            String optValue = option.substring(split + 1).trim();
            switch (key) {
                case "name" -> options.setName(optValue);
                case "exact" -> options.setExact(parseBooleanOption("exact", optValue));
                case "checked" -> options.setChecked(parseBooleanOption("checked", optValue));
                case "disabled" -> options.setDisabled(parseBooleanOption("disabled", optValue));
                case "expanded" -> options.setExpanded(parseBooleanOption("expanded", optValue));
                case "pressed" -> options.setPressed(parseBooleanOption("pressed", optValue));
                case "selected" -> options.setSelected(parseBooleanOption("selected", optValue));
                case "includehidden" -> options.setIncludeHidden(parseBooleanOption("includeHidden", optValue));
                case "level" -> options.setLevel(parseIntOption("level", optValue));
                case "index" -> index = parseIntOption("index", optValue);
                default -> throw new IllegalArgumentException("Unsupported role option: " + key);
            }
        }

        Locator locator = page.getByRole(role, options);
        return index == null ? locator : locator.nth(index);
    }

    private boolean parseBooleanOption(String name, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean for " + name + ": " + value);
    }

    private int parseIntOption(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + name + ": " + value, e);
        }
    }

    private String cssString(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String cssEscape(String input) {
        StringBuilder builder = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                builder.append(c);
            } else {
                builder.append('\\').append(c);
            }
        }
        return builder.toString();
    }

}
