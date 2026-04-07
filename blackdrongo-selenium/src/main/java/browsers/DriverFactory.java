package browsers;

import org.openqa.selenium.WebDriver;

public class DriverFactory {

    public static WebDriver getInstance(String browserName) {
        return switch (browserName) {
            case "chrome" -> ChromeDriver.chrome.createChromeDriver();
            case "firefox" -> FireFoxDriver.fireFoxBrowser.createFireFoxDriver();
            default -> throw new IllegalArgumentException("Please provide proper browser Name");
        };
    }

    private static class ChromeDriver {
        private static final ChromeBrowser chrome = new ChromeBrowser();
    }

    private static class FireFoxDriver {
        private static final FireFoxBrowser fireFoxBrowser = new FireFoxBrowser();
    }
}
