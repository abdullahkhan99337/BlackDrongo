package browsers;

import org.openqa.selenium.WebDriver;

public class DriverFactory {

    private static class ChromeDriver{
        private static final ChromeBrowser chrome =  new ChromeBrowser();
    }

    private static class FireFoxDriver{
        private static final FireFoxBrowser fireFoxBrowser =  new FireFoxBrowser();
    }

   public static WebDriver getInstance(String browserName){
       final WebDriver driver;
        switch (browserName){
            case "chrome":
                driver = ChromeDriver.chrome.createChromeDriver();
                break;
            case "firefox":
                driver = FireFoxDriver.fireFoxBrowser.createFireFoxDriver();
                break;
            default:
                throw new IllegalArgumentException("Please provide proper browser Name");
        }
        return driver;
   }
}