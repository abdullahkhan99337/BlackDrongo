package api.request;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

public final class ApiRequestBuilder {

    private ApiRequestBuilder() {
    }

    public static RequestSpecification withQueryParams(RequestSpecification spec, Map<String, ?> params) {
        return params == null || params.isEmpty() ? spec : spec.queryParams(params);
    }

    public static RequestSpecification withPathParams(RequestSpecification spec, Map<String, ?> params) {
        return params == null || params.isEmpty() ? spec : spec.pathParams(params);
    }

    public static RequestSpecification withFormParams(RequestSpecification spec, Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            return spec;
        }
        return spec.contentType("application/x-www-form-urlencoded").formParams(params);
    }

    public static RequestSpecification withHeaders(RequestSpecification spec, Map<String, ?> headers) {
        return headers == null || headers.isEmpty() ? spec : spec.headers(headers);
    }

    public static RequestSpecification withCookies(RequestSpecification spec, Map<String, ?> cookies) {
        return cookies == null || cookies.isEmpty() ? spec : spec.cookies(cookies);
    }

    public static RequestSpecification withRawBody(RequestSpecification spec, String body) {
        return spec.contentType("text/plain").body(body);
    }

    public static RequestSpecification withJsonBody(RequestSpecification spec, String json) {
        return spec.contentType(ContentType.JSON).body(json);
    }

    public static RequestSpecification withJsonBody(RequestSpecification spec, Object body) {
        return spec.contentType(ContentType.JSON).body(body);
    }

    public static RequestSpecification withXmlBody(RequestSpecification spec, String xml) {
        return spec.contentType("application/xml").body(xml);
    }

    public static RequestSpecification withBytesBody(RequestSpecification spec, byte[] bytes) {
        return spec.contentType("application/octet-stream").body(bytes);
    }

    public static RequestSpecification withMultipart(RequestSpecification spec, String controlName, String fileName, byte[] bytes) {
        return spec.multiPart(controlName, fileName, bytes);
    }
}
