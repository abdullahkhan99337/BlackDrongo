package api.data;

import io.restassured.path.json.JsonPath;

import java.io.IOException;
import java.io.InputStream;

public final class ApiJsonData {

    private ApiJsonData() {
    }

    public static JsonPath fromResource(String resourcePath) {
        try (InputStream input = ApiJsonData.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new RuntimeException("JSON data not found in classpath: " + resourcePath);
            }
            byte[] bytes = input.readAllBytes();
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return new JsonPath(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON data: " + resourcePath, e);
        }
    }
}
