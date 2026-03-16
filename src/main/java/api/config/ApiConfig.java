package api.config;

import browsers.ConfigReader;

public final class ApiConfig {

    private static final ConfigReader READER = ConfigReader.getInstance();

    private ApiConfig() {
    }

    public static String baseUrl() {
        return READER.getProperty("api.baseUrl");
    }

    public static String httpbinUrl() {
        return READER.getProperty("api.httpbinUrl");
    }
}
