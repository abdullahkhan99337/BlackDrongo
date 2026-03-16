package api.request;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class ApiHttpMethods {

    private ApiHttpMethods() {
    }

    public static Response get(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().get() : spec.when().get(path);
    }

    public static Response post(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().post() : spec.when().post(path);
    }

    public static Response put(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().put() : spec.when().put(path);
    }

    public static Response patch(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().patch() : spec.when().patch(path);
    }

    public static Response delete(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().delete() : spec.when().delete(path);
    }

    public static Response options(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().options() : spec.when().options(path);
    }

    public static Response head(RequestSpecification spec, String path) {
        return path == null || path.isBlank() ? spec.when().head() : spec.when().head(path);
    }
}
