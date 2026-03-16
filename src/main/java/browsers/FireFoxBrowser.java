package browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.List;

public class FireFoxBrowser {

    private FirefoxOptions getFirefoxOptions() {
        FirefoxOptions options = new FirefoxOptions();
        List<String> arguments = BrowserOptionsReader.getArguments("firefox");
        if (arguments != null && !arguments.isEmpty()) {
            options.addArguments(arguments);
        }
        options.setAcceptInsecureCerts(BrowserOptionsReader.isAcceptInsecureCerts("firefox"));
        return options;
    }

    protected WebDriver createFireFoxDriver() {
        return new FirefoxDriver(getFirefoxOptions());
    }
}
