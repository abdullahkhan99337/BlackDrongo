package browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChromeBrowser {

    private ChromeOptions getChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        List<String> arguments = BrowserOptionsReader.getArguments("chrome");
        if (arguments != null && !arguments.isEmpty()) {
            options.addArguments(arguments);
        }
        if (isHeadlessEnabled()) {
            options.addArguments("--headless=new");
        }
        applyDownloadDirectory(options);
        options.setAcceptInsecureCerts(BrowserOptionsReader.isAcceptInsecureCerts("chrome"));
        return options;
    }

    protected WebDriver createChromeDriver() {
        return new ChromeDriver(getChromeOptions());
    }

    private boolean isHeadlessEnabled() {
        String headless = ConfigReader.getInstance().getOptionalProperty("headless");
        return headless != null && Boolean.parseBoolean(headless);
    }

    private void applyDownloadDirectory(ChromeOptions options) {
        String downloadDir = ConfigReader.getInstance().getOptionalProperty("download.dir");
        if (downloadDir == null || downloadDir.isBlank()) {
            return;
        }
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", Paths.get(downloadDir).toAbsolutePath().toString());
        prefs.put("download.prompt_for_download", false);
        prefs.put("profile.default_content_settings.popups", 0);
        options.setExperimentalOption("prefs", prefs);
    }
}
