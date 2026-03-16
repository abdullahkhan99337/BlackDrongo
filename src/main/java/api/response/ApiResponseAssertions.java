package api.response;

import io.restassured.path.json.JsonPath;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.hamcrest.Matcher;

import java.util.Map;
import java.util.function.Consumer;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static io.restassured.matcher.RestAssuredMatchers.matchesXsdInClasspath;

public final class ApiResponseAssertions {

    private ApiResponseAssertions() {
    }

    public static ValidatableResponse status(Response response, int statusCode) {
        return response.then().statusCode(statusCode);
    }

    public static ValidatableResponse statusLineContains(Response response, String fragment) {
        return response.then().statusLine(org.hamcrest.Matchers.containsString(fragment));
    }

    public static ValidatableResponse headers(Response response, Map<String, String> expected) {
        ValidatableResponse vr = response.then();
        if (expected != null) {
            expected.forEach((k, v) -> vr.header(k, v));
        }
        return vr;
    }

    public static ValidatableResponse cookie(Response response, String name, String value) {
        return response.then().cookie(name, value);
    }

    public static ValidatableResponse body(Response response, String path, Matcher<?> matcher) {
        return response.then().body(path, matcher);
    }

    public static ValidatableResponse jsonPath(Response response, String path, Matcher<?> matcher) {
        return response.then().body(path, matcher);
    }

    public static ValidatableResponse xmlPath(Response response, String path, Matcher<?> matcher) {
        return response.then().body(path, matcher);
    }

    public static ValidatableResponse jsonSchema(Response response, String classpathSchema) {
        return response.then().body(matchesJsonSchemaInClasspath(classpathSchema));
    }

    public static ValidatableResponse xmlSchema(Response response, String classpathSchema) {
        return response.then().body(matchesXsdInClasspath(classpathSchema));
    }

    public static JsonPath asJsonPath(Response response) {
        return response.jsonPath();
    }

    public static XmlPath asXmlPath(Response response) {
        return response.xmlPath();
    }

    public static void custom(Response response, Consumer<ValidatableResponse> validator) {
        if (validator == null) {
            return;
        }
        validator.accept(response.then());
    }
}
