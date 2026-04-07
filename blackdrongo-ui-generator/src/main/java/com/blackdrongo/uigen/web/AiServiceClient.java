package com.blackdrongo.uigen.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiServiceClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AiServiceClient(ObjectMapper objectMapper,
                           @Value("${blackdrongo.ai-service.base-url:http://127.0.0.1:8001}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public GeneratedScenarioDraft generateScenario(ProjectStore.ProjectEntry project,
                                                   FeatureStore.FeatureEntry feature,
                                                   ScenarioRequest scenario) {
        String scenarioName = safe(scenario.getScenarioName());
        String plainText = safe(scenario.getScenarioSteps());
        if (scenarioName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario name is required for AI generation.");
        }
        if (plainText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario steps are required for AI generation.");
        }

        GenerateTestsPayload payload = new GenerateTestsPayload(
                safe(project.project().getProjectName()),
                pickBaseUrl(project, feature, scenario),
                safe(feature.request().getFeatureName()),
                scenarioName,
                plainText,
                "steps.generated",
                null
        );
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> requestEntity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
            GenerateTestsResponse body = restTemplate.postForObject(baseUrl + "/api/v1/generate/tests", requestEntity, GenerateTestsResponse.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned an empty response.");
            }
            return new GeneratedScenarioDraft(
                    extractScenarioSteps(body.feature != null ? body.feature.content : ""),
                    body.provider == null || body.provider.isBlank() ? "mock" : body.provider,
                    body.assumptions == null ? List.of() : body.assumptions,
                    body.stepDefinitions != null ? body.stepDefinitions.content : "",
                    body.pageObject != null ? body.pageObject.content : ""
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned " + e.getStatusCode().value() + ".", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to build AI request.", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach AI service.", e);
        }
    }

    private String extractScenarioSteps(String featureContent) {
        if (featureContent == null || featureContent.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : featureContent.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith("Given ") || line.startsWith("When ") || line.startsWith("Then ") || line.startsWith("And ") || line.startsWith("But ")) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    private String pickBaseUrl(ProjectStore.ProjectEntry project,
                               FeatureStore.FeatureEntry feature,
                               ScenarioRequest scenario) {
        String scenarioUrl = safe(scenario.getBaseUrl());
        if (!scenarioUrl.isBlank()) {
            return scenarioUrl;
        }
        String featureUrl = safe(feature.request().getBaseUrl());
        if (!featureUrl.isBlank()) {
            return featureUrl;
        }
        return safe(project.project().getBaseUrl());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record GeneratedScenarioDraft(String scenarioSteps,
                                         String provider,
                                         List<String> assumptions,
                                         String stepDefinitionsPreview,
                                         String pageObjectPreview) {
    }

    private record GenerateTestsPayload(@JsonProperty("project_name") String projectName,
                                        @JsonProperty("base_url") String baseUrl,
                                        @JsonProperty("feature_name") String featureName,
                                        @JsonProperty("scenario_name") String scenarioName,
                                        @JsonProperty("plain_text") String plainText,
                                        @JsonProperty("package_name") String packageName,
                                        @JsonProperty("page_class_name") String pageClassName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GenerateTestsResponse {
        public GeneratedArtifact feature;
        @JsonProperty("step_definitions")
        public GeneratedArtifact stepDefinitions;
        @JsonProperty("page_object")
        public GeneratedArtifact pageObject;
        public List<String> assumptions;
        public String provider;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeneratedArtifact {
        public String fileName;
        public String content;
    }
}
