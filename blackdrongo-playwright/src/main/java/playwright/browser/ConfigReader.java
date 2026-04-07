package playwright.browser;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

public final class ConfigReader {

    private static final ConfigReader INSTANCE = new ConfigReader();
    private final Properties properties = new Properties();

    private ConfigReader() {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }
    }

    public static ConfigReader getInstance() {
        return INSTANCE;
    }

    public String getProperty(String key) {
        String value = getOptionalProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return value;
    }

    public String getOptionalProperty(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }

        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }

        String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String fileValue = properties.getProperty(key);
        return fileValue == null ? "" : fileValue.trim();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getOptionalProperty(key);
        if (value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
