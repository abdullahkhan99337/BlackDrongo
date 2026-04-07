package browsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {


    private static final String propertyFilePath = "config.properties";
    private static volatile ConfigReader reader = null;
    private static Properties properties;

    private ConfigReader() {
    }

    public static ConfigReader getInstance() {
        if (reader == null) {
            synchronized (ConfigReader.class) {
                if (reader == null) {
                    readFile();
                    reader = new ConfigReader();
                }
            }
        }
        return reader;
    }

    public static void readFile() {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream(propertyFilePath)) {
            if (input == null) {
                throw new RuntimeException("Configuration.properties not found at " + propertyFilePath);
            }
            properties = new Properties();
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + propertyFilePath, e);
        }
    }

    public String getOptionalProperty(String key) {
        if (properties == null) {
            synchronized (ConfigReader.class) {
                if (properties == null) {
                    readFile();
                }
            }
        }
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String value = properties.getProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }

    public String getProperty(String key) {
        String value = getOptionalProperty(key);
        if (value != null && !value.isBlank()) {
            return value;
        }
        throw new RuntimeException(key + " not specified in the Configuration.properties file.");
    }


}
