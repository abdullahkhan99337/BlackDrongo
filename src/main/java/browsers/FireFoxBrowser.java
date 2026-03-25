package browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireFoxBrowser {

    private FirefoxOptions getFirefoxOptions() {
        FirefoxOptions options = new FirefoxOptions();
        List<String> arguments = BrowserOptionsReader.getArguments("firefox");
        if (arguments != null && !arguments.isEmpty()) {
            options.addArguments(arguments);
        }
        if (isHeadlessEnabled()) {
            options.addArguments("-headless");
        }
        applyDownloadDirectory(options);
        options.setAcceptInsecureCerts(BrowserOptionsReader.isAcceptInsecureCerts("firefox"));
        return options;
    }

    protected WebDriver createFireFoxDriver() {
        return new FirefoxDriver(getFirefoxOptions());
    }

    private boolean isHeadlessEnabled() {
        String headless = ConfigReader.getInstance().getOptionalProperty("headless");
        return headless != null && Boolean.parseBoolean(headless);
    }

    private void applyDownloadDirectory(FirefoxOptions options) {
        String downloadDir = ConfigReader.getInstance().getOptionalProperty("download.dir");
        if (downloadDir == null || downloadDir.isBlank()) {
            return;
        }
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("browser.download.folderList", 2);
        prefs.put("browser.download.dir", Paths.get(downloadDir).toAbsolutePath().toString());
        prefs.put("browser.helperApps.neverAsk.saveToDisk", "application/pdf,application/octet-stream");
        prefs.put("browser.download.manager.showWhenStarting", false);
        options.addPreference("browser.download.folderList", (Integer) prefs.get("browser.download.folderList"));
        options.addPreference("browser.download.dir", (String) prefs.get("browser.download.dir"));
        options.addPreference("browser.helperApps.neverAsk.saveToDisk", (String) prefs.get("browser.helperApps.neverAsk.saveToDisk"));
        options.addPreference("browser.download.manager.showWhenStarting", (Boolean) prefs.get("browser.download.manager.showWhenStarting"));
    }
}
