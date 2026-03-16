package browsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {


    private static volatile ConfigReader reader = null;
    private static Properties properties;
    private static final String propertyFilePath = "config.properties";

    private ConfigReader(){}

    public static ConfigReader getInstance(){
        if (reader == null){
            synchronized (ConfigReader.class){
                if (reader == null){
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

    public String getProperty(String key) {
        if (properties == null) {
            synchronized (ConfigReader.class) {
                if (properties == null) {
                    readFile();
                }
            }
        }
        String value = properties.getProperty(key);
        if (value != null) {
            return value;
        } else {
            throw new RuntimeException(key + " not specified in the Configuration.properties file.");
        }
    }


}
