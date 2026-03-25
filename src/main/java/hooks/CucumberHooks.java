package hooks;

import browsers.BrowserManager;
import browsers.ConfigReader;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;

import java.io.File;
import java.util.logging.Logger;

public class CucumberHooks {

    private static final Logger LOGGER = Logger.getLogger(CucumberHooks.class.getName());

    @Before
    public void beforeScenario(Scenario scenario) {
        BrowserManager manager = BrowserManager.getInstance();
        String baseUrl = ConfigReader.getInstance().getOptionalProperty("url");
        if (baseUrl != null && !baseUrl.isBlank()) {
            manager.open(baseUrl);
        }
        LOGGER.info(() -> "Thread=" + Thread.currentThread().getId() + " | Starting scenario: " + scenario.getName());
    }

    @BeforeStep
    public void beforeStep(Scenario scenario) {
        LOGGER.info(() -> "Thread=" + Thread.currentThread().getId() + " | Executing step in scenario: " + scenario.getName());
    }

    @AfterStep
    public void afterStep(Scenario scenario) {
        LOGGER.info(() -> "Thread=" + Thread.currentThread().getId() + " | Completed step in scenario: " + scenario.getName());
    }

    @After
    public void afterScenario(Scenario scenario) {
        if (scenario.isFailed()) {
            String enabled = ConfigReader.getInstance().getOptionalProperty("screenshot.on.failure");
            if (enabled == null || Boolean.parseBoolean(enabled)) {
                takeFailureScreenshot(scenario);
            }
        }
        BrowserManager.getInstance().quitBrowser();
        LOGGER.info(() -> "Thread=" + Thread.currentThread().getId() + " | Finished scenario: " + scenario.getName() + " | status=" + scenario.getStatus());
    }

    private void takeFailureScreenshot(Scenario scenario) {
        try {
            byte[] screenshot = ((org.openqa.selenium.TakesScreenshot) BrowserManager.getInstance().getDriver())
                    .getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            scenario.attach(screenshot, "image/png", scenario.getName());
            String targetDir = new File("target/screenshots").getAbsolutePath();
            new File(targetDir).mkdirs();
            String filename = scenario.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
            java.nio.file.Files.write(java.nio.file.Path.of(targetDir, filename), screenshot);
        } catch (Exception e) {
            LOGGER.warning("Failed to capture screenshot: " + e.getMessage());
        }
    }
}
