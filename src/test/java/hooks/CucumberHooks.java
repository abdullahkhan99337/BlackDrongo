package hooks;

import browsers.BrowserManager;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;

import java.util.logging.Logger;

public class CucumberHooks {

    private static final Logger LOGGER = Logger.getLogger(CucumberHooks.class.getName());

    @Before
    public void beforeScenario(Scenario scenario) {
        BrowserManager.getInstance().open("https://www.naukri.com/");
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
        BrowserManager.getInstance().quitBrowser();
        LOGGER.info(() -> "Thread=" + Thread.currentThread().getId() + " | Finished scenario: " + scenario.getName() + " | status=" + scenario.getStatus());
    }
}