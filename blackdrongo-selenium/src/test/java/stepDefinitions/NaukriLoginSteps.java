package stepDefinitions;

import browsers.BrowserManager;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import pageObjects.NaukriLoginPage;
import steps.Step;
import utils.Utility;

import java.net.URISyntaxException;

import static io.restassured.RestAssured.given;

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

        step.setWebElement(NaukriLoginPage.userProfileMenu)
                .waitForVisible()
                .click();

        step.setWebElement(NaukriLoginPage.resumeFileInput)
                .uploadFile(Utility.getAbsolutePath("src/test/resources/Resume.docx"));
    }

    @Then("user should see success message")
    public void userShouldSeeSuccessMessage() {
        step.setWebElement(NaukriLoginPage.resumeUploadSuccessMessage)
                .waitForVisible()
                .verifyDisplayed();
    }
}
