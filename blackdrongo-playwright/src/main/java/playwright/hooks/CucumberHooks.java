package playwright.hooks;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import playwright.browser.PlaywrightManager;

public class CucumberHooks {

    @AfterAll
    public static void afterAll() {
        PlaywrightManager.getInstance().stop();
    }

    @Before
    public void beforeScenario() {
        PlaywrightManager manager = PlaywrightManager.getInstance();
        manager.start();
        manager.openBaseUrl();
    }

    @After
    public void afterScenario() {
        PlaywrightManager.getInstance().closeThreadSession();
    }
}
