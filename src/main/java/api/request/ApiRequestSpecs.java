package api.request;

import api.config.ApiConfig;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

public final class ApiRequestSpecs {

    private ApiRequestSpecs() {
    }

    public static RequestSpecification baseSpec() {
        return new RequestSpecBuilder()
            .setBaseUri(ApiConfig.baseUrl())
            .build();
    }

    public static RequestSpecification spec(String baseUri) {
        return new RequestSpecBuilder()
            .setBaseUri(baseUri)
            .build();
    }

    public static RequestSpecification spec(String baseUri, String basePath, Map<String, String> headers) {
        RequestSpecBuilder builder = new RequestSpecBuilder()
            .setBaseUri(baseUri)
            .setContentType(ContentType.JSON);

        if (basePath != null && !basePath.isBlank()) {
            builder.setBasePath(basePath);
        }
        if (headers != null && !headers.isEmpty()) {
            builder.addHeaders(headers);
        }
        return builder.build();
    }
}
