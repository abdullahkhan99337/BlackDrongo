package stepDefinitions;

import browsers.BrowserManager;
import browsers.ConfigReader;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pageObjects.NaukriLoginPage;
import steps.Step;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public class NaukriLoginSteps {

    private BrowserManager browserManager;
    private Step step;

    @Given("user opens naukri login page")
    public void userOpensNaukriLoginPage() {
        browserManager = BrowserManager.getInstance();
        browserManager.verifyCurrentUrlContains("naukri.com");
        step = Step.create(browserManager.getDriver()).setTimeoutSeconds(20);
    }

    @When("user opens login dialog")
    public void userOpensLoginDialog() {
        step.setWebElement(NaukriLoginPage.loginButton)
                .waitForClickable()
                .click();
    }

    @When("user enters email {string} and password {string}")
    public void userEntersEmailAndPassword(String email, String password) {
        step.setWebElement(NaukriLoginPage.usernameInput)
                .waitForVisible()
                .clear()
                .type(email)

                .setWebElement(NaukriLoginPage.passwordInput)
                .waitForVisible()
                .clear()
                .type(password);
    }

    @When("user submits login")
    public void userSubmitsLogin() {
        step.setWebElement(NaukriLoginPage.submitLoginButton)
                .waitForClickable()
                .click();
    }

    @Then("user should see login error")
    public void userShouldSeeLoginError() {
        step.setWebElement(NaukriLoginPage.loginErrorMessage)
                .waitForVisible()
                .verifyTextContains("match");
    }

    @Then("user updates the profile by uploading resume")
    public void userShouldNavigateToProfile() throws URISyntaxException {
        URL res = getClass().getClassLoader().getResource("Resume.docx");
        File file = Paths.get(res.toURI()).toFile();
        String absolutePath = file.getAbsolutePath();
        step.setWebElement(NaukriLoginPage.userProfileMenu)
                .waitForVisible()
                .click();

        step.setWebElement(NaukriLoginPage.uploadResumeButton)
                .waitForClickable()
                .click();

        step.setWebElement(NaukriLoginPage.resumeFileInput)
                .uploadFile(absolutePath);
    }

    @Then("user should see success message")
    public void userShouldSeeSuccessMessage() {
        step.setWebElement(NaukriLoginPage.resumeUploadSuccessMessage)
                .waitForVisible()
                .verifyDisplayed();
    }

}
