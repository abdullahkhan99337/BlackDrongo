package api.steps;

import api.config.ApiConfig;
import api.data.ApiJsonData;
import api.request.ApiHttpMethods;
import api.request.ApiRequestBuilder;
import api.request.ApiRequestSpecs;
import api.response.ApiResponseAssertions;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matcher;

import java.util.Base64;
import java.util.Map;

public class ApiStep {

    private RequestSpecification spec;
    private Response response;
    private Map<String, Object> pendingPathParams;
    private String currentBasePath;

    private ApiStep(RequestSpecification spec) {
        this.spec = spec;
    }

    public static ApiStep create() {
        return new ApiStep(ApiRequestSpecs.baseSpec());
    }

    public static ApiStep create(String baseUri) {
        return new ApiStep(ApiRequestSpecs.spec(baseUri));
    }

    public ApiStep baseUri(String baseUri) {
        this.spec = ApiRequestSpecs.spec(baseUri);
        return this;
    }

    public ApiStep basePath(String basePath) {
        this.spec = ApiRequestSpecs.spec(currentBaseUri(), basePath, null);
        this.currentBasePath = basePath;
        return this;
    }

    public ApiStep headers(Map<String, String> headers) {
        this.spec = ApiRequestBuilder.withHeaders(this.spec, headers);
        return this;
    }

    public ApiStep header(String name, String value) {
        this.spec = this.spec.header(name, value);
        return this;
    }

    public ApiStep cookies(Map<String, ?> cookies) {
        this.spec = ApiRequestBuilder.withCookies(this.spec, cookies);
        return this;
    }

    public ApiStep cookie(String name, Object value) {
        this.spec = this.spec.cookie(name, value);
        return this;
    }

    public ApiStep queryParams(Map<String, ?> params) {
        this.spec = ApiRequestBuilder.withQueryParams(this.spec, params);
        return this;
    }

    public ApiStep queryParam(String name, Object value) {
        this.spec = this.spec.queryParam(name, value);
        return this;
    }

    public ApiStep pathParams(Map<String, ?> params) {
        this.spec = ApiRequestBuilder.withPathParams(this.spec, params);
        return this;
    }

    public ApiStep pathParam(String name, Object value) {
        this.spec = this.spec.pathParam(name, value);
        return this;
    }

    public ApiStep formParams(Map<String, ?> params) {
        this.spec = ApiRequestBuilder.withFormParams(this.spec, params);
        return this;
    }

    public ApiStep formParam(String name, Object value) {
        this.spec = this.spec.formParam(name, value).contentType("application/x-www-form-urlencoded");
        return this;
    }

    public ApiStep rawBody(String body) {
        this.spec = ApiRequestBuilder.withRawBody(this.spec, body);
        return this;
    }

    public ApiStep jsonBody(String json) {
        this.spec = ApiRequestBuilder.withJsonBody(this.spec, json);
        return this;
    }

    public ApiStep jsonBody(Object body) {
        this.spec = ApiRequestBuilder.withJsonBody(this.spec, body);
        return this;
    }

    public ApiStep xmlBody(String xml) {
        this.spec = ApiRequestBuilder.withXmlBody(this.spec, xml);
        return this;
    }

    public ApiStep bytesBody(byte[] bytes) {
        this.spec = ApiRequestBuilder.withBytesBody(this.spec, bytes);
        return this;
    }

    public ApiStep multipart(String controlName, String fileName, byte[] bytes) {
        this.spec = ApiRequestBuilder.withMultipart(this.spec, controlName, fileName, bytes);
        return this;
    }

    public ApiStep multipart(String controlName, String fileName, byte[] bytes, String contentType) {
        this.spec = this.spec.multiPart(controlName, fileName, bytes, contentType);
        return this;
    }

    public ApiStep loadData(String resourcePath) {
        JsonPath json = ApiJsonData.fromResource(resourcePath);
        applyJsonData(json);
        return this;
    }

    public ApiStep get(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.get(this.spec, path);
        return this;
    }

    public ApiStep post(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.post(this.spec, path);
        return this;
    }

    public ApiStep put(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.put(this.spec, path);
        return this;
    }

    public ApiStep patch(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.patch(this.spec, path);
        return this;
    }

    public ApiStep delete(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.delete(this.spec, path);
        return this;
    }

    public ApiStep options(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.options(this.spec, path);
        return this;
    }

    public ApiStep head(String path) {
        applyPendingPathParams(path);
        this.response = ApiHttpMethods.head(this.spec, path);
        return this;
    }

    public Response response() {
        return this.response;
    }

    public ApiStep assertStatus(int status) {
        ApiResponseAssertions.status(response, status);
        return this;
    }

    public ApiStep assertStatusLineContains(String fragment) {
        ApiResponseAssertions.statusLineContains(response, fragment);
        return this;
    }

    public ApiStep assertHeader(String name, String value) {
        ApiResponseAssertions.headers(response, Map.of(name, value));
        return this;
    }

    public ApiStep assertCookie(String name, String value) {
        ApiResponseAssertions.cookie(response, name, value);
        return this;
    }

    public ApiStep assertBody(String path, Matcher<?> matcher) {
        ApiResponseAssertions.body(response, path, matcher);
        return this;
    }

    public ApiStep assertJsonPath(String path, Matcher<?> matcher) {
        ApiResponseAssertions.jsonPath(response, path, matcher);
        return this;
    }

    public ApiStep assertXmlPath(String path, Matcher<?> matcher) {
        ApiResponseAssertions.xmlPath(response, path, matcher);
        return this;
    }

    public ApiStep assertJsonSchema(String classpathSchema) {
        ApiResponseAssertions.jsonSchema(response, classpathSchema);
        return this;
    }

    public ApiStep assertXmlSchema(String classpathSchema) {
        ApiResponseAssertions.xmlSchema(response, classpathSchema);
        return this;
    }

    public ApiStep assertCustom(java.util.function.Consumer<ValidatableResponse> validator) {
        ApiResponseAssertions.custom(response, validator);
        return this;
    }

    private void applyJsonData(JsonPath json) {
        String baseUri = json.getString("baseUri");
        String basePath = json.getString("basePath");
        if (baseUri == null || baseUri.isBlank()) {
            baseUri = ApiConfig.baseUrl();
        }

        Map<String, String> headers = json.getMap("headers");
        this.spec = ApiRequestSpecs.spec(baseUri, basePath, headers);
        this.currentBasePath = basePath;

        Map<String, Object> queryParams = json.getMap("queryParams");
        Map<String, Object> pathParams = json.getMap("pathParams");
        Map<String, Object> cookies = json.getMap("cookies");
        Map<String, Object> formParams = json.getMap("formParams");

        this.spec = ApiRequestBuilder.withQueryParams(this.spec, queryParams);
        this.pendingPathParams = pathParams;
        this.spec = ApiRequestBuilder.withCookies(this.spec, cookies);

        if (formParams != null && !formParams.isEmpty()) {
            this.spec = ApiRequestBuilder.withFormParams(this.spec, formParams);
        }

        String bodyType = json.getString("body.type");
        if (bodyType != null && !bodyType.isBlank()) {
            this.spec = applyBody(this.spec, json, bodyType);
        }

        Object multipart = json.get("multipart");
        if (multipart != null) {
            this.spec = applyMultipart(this.spec, json);
        }
    }

    private RequestSpecification applyBody(RequestSpecification spec, JsonPath json, String bodyType) {
        String type = bodyType.trim().toLowerCase();
        String contentType = json.getString("body.contentType");

        switch (type) {
            case "json" -> {
                Object content = json.get("body.content");
                RequestSpecification s = content == null
                    ? ApiRequestBuilder.withJsonBody(spec, "")
                    : ApiRequestBuilder.withJsonBody(spec, content);
                return contentType == null || contentType.isBlank() ? s : s.contentType(contentType);
            }
            case "xml" -> {
                String xml = json.getString("body.content");
                RequestSpecification s = ApiRequestBuilder.withXmlBody(spec, xml == null ? "" : xml);
                return contentType == null || contentType.isBlank() ? s : s.contentType(contentType);
            }
            case "raw" -> {
                String raw = json.getString("body.content");
                RequestSpecification s = ApiRequestBuilder.withRawBody(spec, raw == null ? "" : raw);
                return contentType == null || contentType.isBlank() ? s : s.contentType(contentType);
            }
            case "bytes" -> {
                String raw = json.getString("body.content");
                byte[] bytes = raw == null || raw.isBlank() ? new byte[0] : Base64.getDecoder().decode(raw);
                RequestSpecification s = ApiRequestBuilder.withBytesBody(spec, bytes);
                return contentType == null || contentType.isBlank() ? s : s.contentType(contentType);
            }
            case "none" -> {
                return spec;
            }
            default -> throw new IllegalArgumentException("Unsupported body type: " + type);
        }
    }

    private RequestSpecification applyMultipart(RequestSpecification spec, JsonPath json) {
        String controlName = json.getString("multipart.controlName");
        String fileName = json.getString("multipart.fileName");
        String contentType = json.getString("multipart.contentType");
        String content = json.getString("multipart.content");
        Boolean base64 = json.getBoolean("multipart.base64");

        if (controlName == null || controlName.isBlank()) {
            throw new IllegalArgumentException("multipart.controlName is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("multipart.content is required");
        }

        byte[] bytes = Boolean.TRUE.equals(base64) ? Base64.getDecoder().decode(content) : content.getBytes();

        if (contentType != null && !contentType.isBlank()) {
            return spec.multiPart(controlName, fileName == null ? "file" : fileName, bytes, contentType);
        }
        return ApiRequestBuilder.withMultipart(spec, controlName, fileName == null ? "file" : fileName, bytes);
    }

    private String currentBaseUri() {
        return ApiConfig.baseUrl();
    }

    private boolean shouldApplyPathParams(Map<String, Object> params, String basePath, String path) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String combined = (basePath == null ? "" : basePath) + (path == null ? "" : path);
        for (String key : params.keySet()) {
            if (combined.contains("{" + key + "}")) {
                return true;
            }
        }
        return false;
    }

    private void applyPendingPathParams(String path) {
        if (pendingPathParams == null || pendingPathParams.isEmpty()) {
            return;
        }
        if (shouldApplyPathParams(pendingPathParams, currentBasePath, path)) {
            this.spec = ApiRequestBuilder.withPathParams(this.spec, pendingPathParams);
        }
    }
}
