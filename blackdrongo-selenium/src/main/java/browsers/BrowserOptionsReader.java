package browsers;

import io.restassured.path.json.JsonPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class BrowserOptionsReader {

    private static final String OPTIONS_FILE = "browserOptions.json";
    private static final JsonPath JSON = loadJson();

    private BrowserOptionsReader() {
    }

    public static List<String> getArguments(String browser) {
        return JSON.getList(path(browser, "arguments"));
    }

    public static boolean isAcceptInsecureCerts(String browser) {
        return Boolean.TRUE.equals(JSON.getBoolean(path(browser, "acceptInsecureCerts")));
    }

    private static String path(String browser, String field) {
        return "browsers." + browser.toLowerCase(Locale.ROOT) + "." + field;
    }

    private static JsonPath loadJson() {
        try (InputStream input = BrowserOptionsReader.class.getClassLoader().getResourceAsStream(OPTIONS_FILE)) {
            if (input == null) {
                throw new RuntimeException(OPTIONS_FILE + " not found in classpath");
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new JsonPath(json);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load " + OPTIONS_FILE, e);
        }
    }
}
