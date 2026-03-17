import browsers.BrowserManager;
import pageObjects.Google;
import steps.ActionType;
import steps.Step;

public class Test {


    public static void main(String[] args) {
        BrowserManager driver = BrowserManager
                .getInstance()
                .open("https://www.google.com/")
                .verifyTitle("Google");

        Step.create(driver.getDriver())
                .setWebElement(Google.search)
                .type("Automation")
                .setWebElement(Google.list)
                .verifyText("automation");

        driver.quitBrowser();


    }
}
