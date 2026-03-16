package browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

public class ChromeBrowser {

    private ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        List<String> arguments = BrowserOptionsReader.getArguments("chrome");
        if (arguments != null && !arguments.isEmpty()) {
            options.addArguments(arguments);
        }
        options.setAcceptInsecureCerts(BrowserOptionsReader.isAcceptInsecureCerts("chrome"));
        return options;
    }

    protected WebDriver createChromeDriver() {
        return new ChromeDriver(getChromeOptions());
    }
}