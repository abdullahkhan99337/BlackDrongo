package com.blackdrongo.uigen.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateGenerator {

    public static String sanitizeProjectName(String value) {
        if (value == null || value.isBlank()) {
            return "automation-template";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public GenerationResult generate(GenerateRequest request) throws IOException {
        String engine = normalizeEngine(request.getEngine());
        boolean projectMode = request.isProjectMode();
        Path outputRoot = AppPaths.generatedDir().toAbsolutePath();
        String safeName = sanitizeProjectName(request.getProjectName());
        String workspaceName = workspaceName(engine);
        Path projectRoot = projectMode ? outputRoot.resolve(safeName) : outputRoot.resolve(workspaceName);
        Files.createDirectories(projectRoot);
        if (projectMode) {
            ensureEngineMatches(projectRoot, engine);
        }
        ScenarioData scenario = projectMode
                ? buildFeatureFile(request, request.getFeatureScenarios(), true)
                : buildScenario(request, false);

        if (!projectMode) {
            cleanScenarioArtifacts(projectRoot);
        }
        if ("selenium".equals(engine)) {
            generateSeleniumTemplate(projectRoot, request, scenario, projectMode ? safeName : workspaceName);
        } else if ("playwright".equals(engine)) {
            generatePlaywrightTemplate(projectRoot, request, scenario, projectMode ? safeName : workspaceName);
        } else {
            throw new IllegalArgumentException("Unsupported engine: " + request.getEngine());
        }
        return new GenerationResult(projectRoot, scenario, projectMode ? safeName : workspaceName);
    }

    private void generateSeleniumTemplate(Path root, GenerateRequest request, ScenarioData scenario, String artifactId) throws IOException {
        writeIfMissing(root.resolve("pom.xml"), seleniumPom(artifactId));
        writeIfMissing(root.resolve("testng.xml"), testngXml());
        write(root.resolve("src/test/resources/config.properties"), seleniumConfig(request));
        write(root.resolve("src/test/resources/browserOptions.json"), browserOptionsJson(request));
        Path featuresDir = root.resolve("src/test/resources/features");
        write(featuresDir.resolve(scenario.featureFileName), scenario.featureText);
        String stepsPackage = request.isProjectMode() ? "steps.shared" : "steps";
        write(root.resolve("src/test/java/runner/CucumberTestNgRunner.java"),
                cucumberRunnerWithHooks(stepsPackage, "hooks"));
        List<StepDef> stepDefs = scenario.stepDefinitions;
        Path stepsFile = root.resolve("src/test/java")
                .resolve(stepsPackage.replace(".", "/"))
                .resolve(scenario.stepsClassName + ".java");
        write(stepsFile, seleniumSteps(stepDefs, scenario.stepsClassName, scenario.pageClassName, stepsPackage));
        Path pageFile = root.resolve("src/test/java/pages/" + scenario.pageClassName + ".java");
        write(pageFile, seleniumPageObject(stepDefs, scenario.pageClassName));
        Files.deleteIfExists(root.resolve("src/test/java/steps/Step.java"));
    }

    private void generatePlaywrightTemplate(Path root, GenerateRequest request, ScenarioData scenario, String artifactId) throws IOException {
        writeIfMissing(root.resolve("pom.xml"), playwrightPom(artifactId));
        writeIfMissing(root.resolve("testng.xml"), testngXml());
        write(root.resolve("src/test/resources/config.properties"), playwrightConfig(request));
        Path featuresDir = root.resolve("src/test/resources/features");
        write(featuresDir.resolve(scenario.featureFileName), scenario.featureText);
        String stepsPackage = request.isProjectMode() ? "steps.shared" : "steps";
        write(root.resolve("src/test/java/runner/CucumberTestNgRunner.java"),
                cucumberRunnerWithHooks(stepsPackage, "playwright.hooks"));
        List<StepDef> stepDefs = scenario.stepDefinitions;
        Path stepsFile = root.resolve("src/test/java")
                .resolve(stepsPackage.replace(".", "/"))
                .resolve(scenario.stepsClassName + ".java");
        write(stepsFile, playwrightSteps(stepDefs, scenario.stepsClassName, scenario.pageClassName, stepsPackage));
        Path pageFile = root.resolve("src/test/java/pages/" + scenario.pageClassName + ".java");
        if (request.isProjectMode()) {
            ensurePageMethods(pageFile, stepDefs, false, scenario.pageClassName);
        } else {
            write(pageFile, playwrightPageObject(stepDefs, scenario.pageClassName));
        }
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void writeIfMissing(Path file, String content) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        write(file, content);
    }

    private void cleanScenarioArtifacts(Path root) throws IOException {
        deleteDirectory(root.resolve("src/test/resources/features"));
        deleteDirectory(root.resolve("src/test/java/steps"));
        deleteDirectory(root.resolve("src/test/java/pages"));
    }

    private void ensureEngineMatches(Path root, String engine) throws IOException {
        Path marker = root.resolve(".engine");
        Path pom = root.resolve("pom.xml");
        if (Files.exists(marker)) {
            String existing = Files.readString(marker, StandardCharsets.UTF_8).trim();
            if (!existing.equalsIgnoreCase(engine)) {
                throw new IllegalArgumentException("Project already exists for engine: " + existing);
            }
            return;
        }
        if (Files.exists(pom)) {
            String pomText = Files.readString(pom, StandardCharsets.UTF_8);
            String detected = detectEngineFromPom(pomText);
            if (detected != null && !detected.equalsIgnoreCase(engine)) {
                throw new IllegalArgumentException("Project already exists for engine: " + detected);
            }
        }
        Files.writeString(marker, engine, StandardCharsets.UTF_8);
    }

    private String detectEngineFromPom(String pomText) {
        if (pomText == null) {
            return null;
        }
        String lower = pomText.toLowerCase(Locale.ROOT);
        if (lower.contains("playwright")) {
            return "playwright";
        }
        if (lower.contains("selenium-java")) {
            return "selenium";
        }
        return null;
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + path, e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private String workspaceName(String engine) {
        return "automation-workspace-" + engine;
    }

    private String normalizeEngine(String engine) {
        if (engine == null) {
            return "selenium";
        }
        return engine.toLowerCase(Locale.ROOT).trim();
    }

    private String seleniumPom(String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                        <maven.compiler.release>25</maven.compiler.release>
                        <cucumber.version>7.25.0</cucumber.version>
                        <testng.version>7.10.2</testng.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-java</artifactId>
                            <version>${cucumber.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-testng</artifactId>
                            <version>${cucumber.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.testng</groupId>
                            <artifactId>testng</artifactId>
                            <version>${testng.version}</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.seleniumhq.selenium</groupId>
                            <artifactId>selenium-java</artifactId>
                            <version>4.40.0</version>
                        </dependency>
                        <dependency>
                            <groupId>com.blackdrongo</groupId>
                            <artifactId>blackdrongo-selenium</artifactId>
                            <version>0.0.1-SNAPSHOT</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                                <configuration>
                                    <suiteXmlFiles>
                                        <suiteXmlFile>testng.xml</suiteXmlFile>
                                    </suiteXmlFiles>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(name);
    }

    private String playwrightPom(String name) {
        return """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>%s</artifactId>
                            <version>0.0.1-SNAPSHOT</version>
                            <properties>
                <maven.compiler.release>25</maven.compiler.release>
                                <cucumber.version>7.25.0</cucumber.version>
                                <testng.version>7.10.2</testng.version>
                            </properties>
                            <dependencies>
                                <dependency>
                                    <groupId>io.cucumber</groupId>
                                    <artifactId>cucumber-java</artifactId>
                                    <version>${cucumber.version}</version>
                                </dependency>
                                <dependency>
                                    <groupId>io.cucumber</groupId>
                                    <artifactId>cucumber-testng</artifactId>
                                    <version>${cucumber.version}</version>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.testng</groupId>
                                    <artifactId>testng</artifactId>
                                    <version>${testng.version}</version>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>com.microsoft.playwright</groupId>
                                    <artifactId>playwright</artifactId>
                                    <version>1.46.0</version>
                                </dependency>
                                <dependency>
                                    <groupId>com.blackdrongo</groupId>
                                    <artifactId>blackdrongo-playwright</artifactId>
                                    <version>0.0.1-SNAPSHOT</version>
                                    <scope>test</scope>
                                </dependency>
                                <dependency>
                                    <groupId>org.slf4j</groupId>
                                    <artifactId>slf4j-simple</artifactId>
                                    <version>2.0.13</version>
                                    <scope>test</scope>
                                </dependency>
                            </dependencies>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <version>3.2.5</version>
                                        <configuration>
                                            <suiteXmlFiles>
                                                <suiteXmlFile>testng.xml</suiteXmlFile>
                                            </suiteXmlFiles>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                """.formatted(name);
    }

    private String seleniumConfig(GenerateRequest request) {
        return """
                browser=%s
                headless=%s
                url=%s
                """.formatted(
                valueOr(request.getBrowser(), "chrome"),
                request.isHeadless(),
                valueOr(request.getBaseUrl(), "")
        );
    }

    private String playwrightConfig(GenerateRequest request) {
        return """
                browser=%s
                headless=%s
                url=%s
                """.formatted(
                valueOr(request.getBrowser(), "chromium"),
                request.isHeadless(),
                valueOr(request.getBaseUrl(), "")
        );
    }

    private ScenarioData buildScenario(GenerateRequest request, boolean sharedClasses) {
        String scenarioName = valueOr(request.getScenarioName(), "Sample scenario");
        String tagsLine = normalizeTags(request.getScenarioTags());
        List<String> stepLines = normalizeSteps(valueOr(request.getScenarioSteps(),
                "Given I open the application"));
        String featureName = "Feature: " + sanitizeFeatureName(
                valueOr(request.getFeatureName(), request.getProjectName()));
        StringBuilder feature = new StringBuilder();
        feature.append(featureName).append("\n\n");
        if (!tagsLine.isBlank()) {
            feature.append("  ").append(tagsLine).append("\n");
        }
        feature.append("  Scenario: ").append(scenarioName).append("\n");
        for (String line : stepLines) {
            feature.append("    ").append(line).append("\n");
        }
        String featureFileName = sanitizeProjectName(scenarioName).toLowerCase(Locale.ROOT) + ".feature";
        if (featureFileName.isBlank()) {
            featureFileName = "sample.feature";
        }
        List<StepDef> stepDefs = toStepDefinitions(stepLines);
        String stepsClassName = sharedClasses ? "SampleSteps" : toClassName(scenarioName, "Steps");
        String pageClassName = sharedClasses ? "AppPage" : toClassName(scenarioName, "Page");
        return new ScenarioData(feature.toString(), featureFileName, stepDefs, stepsClassName, pageClassName);
    }

    private ScenarioData buildFeatureFile(GenerateRequest request, List<ScenarioRequest> scenarios, boolean sharedClasses) {
        String featureTitle = sanitizeFeatureName(valueOr(request.getFeatureName(), request.getProjectName()));
        StringBuilder feature = new StringBuilder();
        feature.append("Feature: ").append(featureTitle).append("\n\n");
        List<ScenarioRequest> data = (scenarios == null || scenarios.isEmpty())
                ? List.of(toScenarioRequest(request))
                : scenarios;
        List<StepDef> stepDefs = new ArrayList<>();
        for (ScenarioRequest scenario : data) {
            String scenarioName = valueOr(scenario.getScenarioName(), "Sample scenario");
            String tagsLine = normalizeTags(scenario.getScenarioTags());
            List<String> stepLines = normalizeSteps(valueOr(scenario.getScenarioSteps(),
                    "Given I open the application"));
            if (!tagsLine.isBlank()) {
                feature.append("  ").append(tagsLine).append("\n");
            }
            feature.append("  Scenario: ").append(scenarioName).append("\n");
            for (String line : stepLines) {
                feature.append("    ").append(line).append("\n");
            }
            feature.append("\n");
            stepDefs.addAll(toStepDefinitions(stepLines));
        }
        List<StepDef> uniqueDefs = uniqueStepDefs(stepDefs);
        String featureFileName = featureFileNameForFeature(request);
        String stepsClassName = toClassName(featureTitle, "Steps");
        String pageClassName = toClassName(featureTitle, "Page");
        return new ScenarioData(feature.toString().trim() + "\n", featureFileName, uniqueDefs,
                stepsClassName, pageClassName);
    }

    private ScenarioRequest toScenarioRequest(GenerateRequest request) {
        ScenarioRequest scenario = new ScenarioRequest();
        scenario.setScenarioName(request.getScenarioName());
        scenario.setScenarioSteps(request.getScenarioSteps());
        scenario.setScenarioTags(request.getScenarioTags());
        return scenario;
    }

    private String featureFileNameForFeature(GenerateRequest request) {
        String safeId = sanitizeProjectName(valueOr(request.getFeatureId(), "")).toLowerCase(Locale.ROOT);
        String safeName = sanitizeProjectName(valueOr(request.getFeatureName(), "")).toLowerCase(Locale.ROOT);
        StringBuilder name = new StringBuilder("feature");
        if (!safeId.isBlank()) {
            name.append("-").append(safeId);
        }
        if (!safeName.isBlank()) {
            name.append("-").append(safeName);
        }
        return name.append(".feature").toString();
    }

    private String cucumberRunner() {
        return """
                package runner;
                
                import io.cucumber.testng.AbstractTestNGCucumberTests;
                import io.cucumber.testng.CucumberOptions;
                import org.testng.annotations.DataProvider;
                
                @CucumberOptions(
                        features = "src/test/resources/features",
                        glue = {"steps"},
                        plugin = {"pretty", "summary"}
                )
                public class CucumberTestNgRunner extends AbstractTestNGCucumberTests {
                
                    @Override
                    @DataProvider(parallel = false)
                    public Object[][] scenarios() {
                        return super.scenarios();
                    }
                }
                """;
    }

    private String cucumberRunnerWithHooks(String stepsPackage, String hooksPackage) {
        return """
                package runner;
                
                import io.cucumber.testng.AbstractTestNGCucumberTests;
                import io.cucumber.testng.CucumberOptions;
                import org.testng.annotations.DataProvider;
                
                @CucumberOptions(
                        features = "src/test/resources/features",
                        glue = {"%s", "%s"},
                        plugin = {"pretty", "summary"}
                )
                public class CucumberTestNgRunner extends AbstractTestNGCucumberTests {
                
                    @Override
                    @DataProvider(parallel = false)
                    public Object[][] scenarios() {
                        return super.scenarios();
                    }
                }
                """.formatted(hooksPackage, stepsPackage);
    }

    private String seleniumSteps(List<StepDef> defs, String className, String pageClassName, String packageName) {
        StringBuilder methods = new StringBuilder();
        for (StepDef def : defs) {
            methods.append(seleniumStepMethod(def, pageClassName));
        }
        return """
                package %s;
                
                import browsers.BrowserManager;
                import browsers.ConfigReader;
                import io.cucumber.java.en.And;
                import io.cucumber.java.en.But;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;
                import io.cucumber.java.en.When;
                import org.openqa.selenium.WebDriver;
                import pages.%s;
                import steps.Step;
                
                public class %s {
                    private final WebDriver driver = BrowserManager.getInstance().getDriver();
                
                %s
                }
                """.formatted(packageName, pageClassName, className,
                indent(methods.toString(), 4));
    }

    private String playwrightSteps(List<StepDef> defs, String className, String pageClassName, String packageName) {
        StringBuilder methods = new StringBuilder();
        for (StepDef def : defs) {
            methods.append(stepMethod(def, true));
        }
        return """
                package %s;
                
                import io.cucumber.java.en.And;
                import io.cucumber.java.en.But;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;
                import io.cucumber.java.en.When;
                import pages.%s;
                
                public class %s {
                    private final %s page = new %s();
                
                %s
                }
                """.formatted(packageName, pageClassName, className, pageClassName, pageClassName,
                indent(methods.toString(), 4));
    }

    private List<StepDef> collectStepDefinitions(Path featuresDir) throws IOException {
        List<String> lines = new ArrayList<>();
        if (featuresDir != null && Files.exists(featuresDir)) {
            try (var paths = Files.list(featuresDir)) {
                for (Path path : paths.toList()) {
                    if (!path.getFileName().toString().endsWith(".feature")) {
                        continue;
                    }
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (trimmed.isBlank()) {
                            continue;
                        }
                        if (trimmed.startsWith("#") || trimmed.startsWith("@")) {
                            continue;
                        }
                        if (trimmed.startsWith("Feature:") || trimmed.startsWith("Scenario")) {
                            continue;
                        }
                        if (extractKeyword(trimmed) != null) {
                            lines.add(trimmed);
                        }
                    }
                }
            }
        }
        return uniqueStepDefs(toStepDefinitions(lines));
    }

    private List<StepDef> uniqueStepDefs(List<StepDef> defs) {
        Map<String, StepDef> unique = new LinkedHashMap<>();
        for (StepDef def : defs) {
            unique.putIfAbsent(def.pattern, def);
        }
        return ensureUniqueMethodNames(new ArrayList<>(unique.values()));
    }

    private void ensurePageMethods(Path pageFile, List<StepDef> defs, boolean selenium, String pageClassName)
            throws IOException {
        if (!Files.exists(pageFile)) {
            String content = selenium ? seleniumPageObject(defs, pageClassName) : playwrightPageObject(defs, pageClassName);
            write(pageFile, content);
            return;
        }
        String content = Files.readString(pageFile, StandardCharsets.UTF_8);
        Set<String> existingMethods = existingMethodNames(content);
        StringBuilder additions = new StringBuilder();
        for (StepDef def : defs) {
            if (existingMethods.contains(def.methodName)) {
                continue;
            }
            String method = selenium ? seleniumPageMethod(def) : playwrightPageMethod(def);
            additions.append(indent(method, 4));
        }
        if (additions.toString().isBlank()) {
            return;
        }
        String updated = insertBeforeLastBrace(content, "\n" + additions);
        write(pageFile, updated);
    }

    private Set<String> existingMethodNames(String content) {
        Set<String> names = new HashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }
        Pattern pattern = Pattern.compile("public\\s+void\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String insertBeforeLastBrace(String content, String addition) {
        int idx = content.lastIndexOf('}');
        if (idx < 0) {
            return content + addition;
        }
        return content.substring(0, idx) + addition + content.substring(idx);
    }

    private String seleniumPageMethod(StepDef def) {
        boolean openStep = isOpenStep(def.rawText);
        String explicitUrl = extractUrl(def.rawText);
        String body;
        if (openStep) {
            if (explicitUrl.isBlank()) {
                body = "openBaseUrl();";
            } else {
                body = "browser.open(\"" + escapeJavaString(explicitUrl) + "\");";
            }
        } else {
            body = actionForSelenium(def.rawText);
        }
        return """
                public void %s() {
                %s
                }
                
                """.formatted(def.methodName, indent(body, 8));
    }

    private String playwrightPageMethod(StepDef def) {
        boolean openStep = isOpenStep(def.rawText);
        String explicitUrl = extractUrl(def.rawText);
        String body;
        if (openStep) {
            if (explicitUrl.isBlank()) {
                body = "manager.openBaseUrl();";
            } else {
                body = "page.navigate(\"" + escapeJavaString(explicitUrl) + "\");";
            }
        } else {
            body = actionForPlaywright(def.rawText);
        }
        return """
                public void %s() {
                    withPage(page -> {
                %s
                    });
                }
                
                """.formatted(def.methodName, indent(body, 12));
    }

    private String testngXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
                <suite name="Cucumber Suite" verbose="1">
                    <test name="Cucumber Tests">
                        <classes>
                            <class name="runner.CucumberTestNgRunner"/>
                        </classes>
                    </test>
                </suite>
                """;
    }

    private String valueOr(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String sanitizeFeatureName(String value) {
        if (value == null || value.isBlank()) {
            return "Generated feature";
        }
        return value.trim();
    }

    private List<String> normalizeSteps(String stepsText) {
        List<String> normalized = new ArrayList<>();
        String[] lines = stepsText.split("\\r?\\n");
        String lastKeyword = "Given";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            String keyword = extractKeyword(line);
            if (keyword == null) {
                String prefix = normalized.isEmpty() ? "Given" : "And";
                normalized.add(prefix + " " + line);
            } else {
                lastKeyword = keyword;
                normalized.add(capitalizeKeyword(keyword) + line.substring(keyword.length()));
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(lastKeyword + " I open the application");
        }
        return normalized;
    }

    private String extractKeyword(String line) {
        for (String k : List.of("Given", "When", "Then", "And", "But")) {
            if (line.toLowerCase(Locale.ROOT).startsWith(k.toLowerCase(Locale.ROOT) + " ")) {
                return k;
            }
        }
        return null;
    }

    private String capitalizeKeyword(String keyword) {
        return keyword.substring(0, 1).toUpperCase(Locale.ROOT) + keyword.substring(1).toLowerCase(Locale.ROOT);
    }

    private List<StepDef> toStepDefinitions(List<String> steps) {
        List<StepDef> defs = new ArrayList<>();
        for (String step : steps) {
            String keyword = extractKeyword(step);
            String text = keyword == null ? step : step.substring(keyword.length()).trim();
            String method = toMethodName(text);
            String pattern = escapeJavaString("^" + escapeRegexLiteral(text) + "$");
            defs.add(new StepDef(keyword == null ? "Given" : keyword, pattern, method, text));
        }
        return ensureUniqueMethodNames(defs);
    }

    private List<StepDef> ensureUniqueMethodNames(List<StepDef> defs) {
        List<StepDef> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (StepDef def : defs) {
            String base = def.methodName;
            String candidate = base;
            int idx = 1;
            while (used.contains(candidate)) {
                candidate = base + idx;
                idx++;
            }
            used.add(candidate);
            if (candidate.equals(def.methodName)) {
                result.add(def);
            } else {
                result.add(new StepDef(def.keyword, def.pattern, candidate, def.rawText));
            }
        }
        return result;
    }

    private String toMethodName(String text) {
        String cleaned = text.replaceAll("[^a-zA-Z0-9 ]", " ").trim();
        if (cleaned.isBlank()) {
            return "step";
        }
        String[] parts = cleaned.split("\\s+");
        StringBuilder name = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            name.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(parts[i].substring(1).toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }

    private String toClassName(String text, String suffix) {
        String cleaned = text == null ? "" : text.replaceAll("[^a-zA-Z0-9 ]", " ").trim();
        if (cleaned.isBlank()) {
            return "Scenario" + suffix;
        }
        String[] parts = cleaned.split("\\s+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            name.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        if (name.isEmpty()) {
            name.append("Scenario");
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name.insert(0, "Scenario");
        }
        name.append(suffix);
        return name.toString();
    }

    private String stepMethod(StepDef def, boolean callPage) {
        String callLine = callPage
                ? "        page." + def.methodName + "();\n"
                : "        // TODO: implement\n";
        return """
                @%s("%s")
                public void %s() {
                %s
                }
                
                """.formatted(def.keyword, def.pattern, def.methodName, callLine);
    }

    private String seleniumStepMethod(StepDef def, String pageClassName) {
        boolean openStep = isOpenStep(def.rawText);
        String explicitUrl = extractUrl(def.rawText);
        String body;
        if (openStep) {
            if (explicitUrl.isBlank()) {
                body = """
                        String url = ConfigReader.getInstance().getOptionalProperty("url");
                        if (url != null && !url.isBlank()) {
                            BrowserManager.getInstance().open(url);
                        }
                        """;
            } else {
                body = "BrowserManager.getInstance().open(\"" + escapeJavaString(explicitUrl) + "\");";
            }
        } else {
            String locatorRef = pageClassName + "." + locatorConstantName(def.methodName);
            body = seleniumActionForLocator(def.rawText, locatorRef);
        }
        return """
                @%s("%s")
                public void %s() {
                %s
                }
                
                """.formatted(def.keyword, def.pattern, def.methodName, indent(body, 8));
    }

    private String locatorConstantName(String methodName) {
        String value = methodName == null ? "" : methodName.trim();
        if (value.isBlank()) {
            return "LOCATOR";
        }
        String snake = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        snake = snake.replaceAll("[^A-Z0-9_]", "_");
        snake = snake.replaceAll("_+", "_");
        if (snake.startsWith("_")) {
            snake = snake.substring(1);
        }
        if (snake.endsWith("_")) {
            snake = snake.substring(0, snake.length() - 1);
        }
        if (snake.isBlank()) {
            snake = "LOCATOR";
        }
        if (!Character.isJavaIdentifierStart(snake.charAt(0))) {
            snake = "L_" + snake;
        }
        return snake;
    }

    private String seleniumActionForLocator(String text, String locatorRef) {
        String quoted = extractQuoted(text);
        String target = deriveTargetText(text);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (isBulkLinkClickStep(text)) {
            return """
                    Step.create(driver)
                            .setWebElements(%s)
                            .clickAllLinksWithBackNavigation();
                    """.formatted(locatorRef);
        }
        if (lower.contains("click")) {
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForClickable()
                            .click();
                    """.formatted(locatorRef);
        }
        if (lower.contains("type") || lower.contains("enter") || lower.contains("fill")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForVisible()
                            .type("%s");
                    """.formatted(locatorRef, escapeJavaString(value));
        }
        if (lower.contains("select")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForVisible()
                            .selectByVisibleText("%s");
                    """.formatted(locatorRef, escapeJavaString(value));
        }
        if (lower.contains("wait")) {
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForVisible();
                    """.formatted(locatorRef);
        }
        if (lower.contains("verify") || lower.contains("should") || lower.contains("see")) {
            String value = quoted.isBlank() ? (target.isBlank() ? "TODO" : target) : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .verifyTextContains("%s");
                    """.formatted(locatorRef, escapeJavaString(value));
        }
        return """
                // TODO: implement step action for: %s
                """.formatted(escapeJavaString(text));
    }

    private String escapeJavaString(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String escapeRegexLiteral(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
    }

    private String indent(String value, int spaces) {
        if (value.isBlank()) {
            return value;
        }
        String pad = " ".repeat(spaces);
        StringBuilder out = new StringBuilder();
        for (String line : value.split("\\r?\\n")) {
            out.append(pad).append(line).append("\n");
        }
        return out.toString();
    }

    private boolean isOpenStep(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("open") || t.contains("launch") || t.contains("navigate") || t.contains("go to") || t.contains("visit");
    }

    private String actionForSelenium(String text) {
        String quoted = extractQuoted(text);
        String target = deriveTargetText(text);
        String explicitLocator = extractLocator(text);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("click")) {
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForClickable()
                            .click();
                    """.formatted(resolveSeleniumLocator(explicitLocator, locatorForClickSelenium(target)));
        }
        if (lower.contains("type") || lower.contains("enter") || lower.contains("fill")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .type("%s");
                    """.formatted(resolveSeleniumLocator(explicitLocator, locatorForInputSelenium(target)), escapeJavaString(value));
        }
        if (lower.contains("select")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .selectByVisibleText("%s");
                    """.formatted(resolveSeleniumLocator(explicitLocator, locatorForSelectSelenium(target)), escapeJavaString(value));
        }
        if (lower.contains("wait")) {
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .waitForVisible();
                    """.formatted(resolveSeleniumLocator(explicitLocator, locatorForTextSelenium(target)));
        }
        if (lower.contains("verify") || lower.contains("should") || lower.contains("see")) {
            String value = quoted.isBlank() ? (target.isBlank() ? "TODO" : target) : quoted;
            return """
                    Step.create(driver)
                            .setWebElement(%s)
                            .verifyTextContains("%s");
                    """.formatted(resolveSeleniumLocator(explicitLocator, locatorForTextSelenium(value)), escapeJavaString(value));
        }
        return """
                // TODO: implement page action for: %s
                """.formatted(escapeJavaString(text));
    }

    private String actionForPlaywright(String text) {
        String quoted = extractQuoted(text);
        String target = deriveTargetText(text);
        String explicitLocator = extractLocator(text);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("click")) {
            return """
                    %s.click();
                    """.formatted(resolvePlaywrightLocator(explicitLocator, "page.getByText(\"" + escapeJavaString(target.isBlank() ? "TODO" : target) + "\").first()"));
        }
        if (lower.contains("type") || lower.contains("enter") || lower.contains("fill")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    %s.fill("%s");
                    """.formatted(resolvePlaywrightLocator(explicitLocator, "page.getByLabel(\"" + escapeJavaString(target.isBlank() ? "TODO" : target) + "\").first()"), escapeJavaString(value));
        }
        if (lower.contains("select")) {
            String value = quoted.isBlank() ? "TODO" : quoted;
            return """
                    %s.selectOption("%s");
                    """.formatted(resolvePlaywrightLocator(explicitLocator, "page.getByLabel(\"" + escapeJavaString(target.isBlank() ? "TODO" : target) + "\").first()"), escapeJavaString(value));
        }
        if (lower.contains("wait")) {
            return """
                    %s.waitFor();
                    """.formatted(resolvePlaywrightLocator(explicitLocator, "page.getByText(\"" + escapeJavaString(target.isBlank() ? "TODO" : target) + "\").first()"));
        }
        if (lower.contains("verify") || lower.contains("should") || lower.contains("see")) {
            String value = quoted.isBlank() ? (target.isBlank() ? "TODO" : target) : quoted;
            return """
                    String actual = %s.textContent();
                    if (actual == null || !actual.contains("%s")) {
                        throw new AssertionError("Expected text to contain: %s");
                    }
                    """.formatted(resolvePlaywrightLocator(explicitLocator, "page.getByText(\"" + escapeJavaString(value) + "\").first()"),
                    escapeJavaString(value), escapeJavaString(value));
        }
        return """
                // TODO: implement page action for: %s
                """.formatted(escapeJavaString(text));
    }

    private String extractQuoted(String text) {
        if (text == null) {
            return "";
        }
        int firstDouble = text.indexOf('"');
        if (firstDouble >= 0) {
            int secondDouble = text.indexOf('"', firstDouble + 1);
            if (secondDouble > firstDouble) {
                return text.substring(firstDouble + 1, secondDouble);
            }
        }
        int firstSingle = text.indexOf('\'');
        if (firstSingle >= 0) {
            int secondSingle = text.indexOf('\'', firstSingle + 1);
            if (secondSingle > firstSingle) {
                return text.substring(firstSingle + 1, secondSingle);
            }
        }
        return "";
    }

    private String extractLocator(String text) {
        if (text == null) {
            return "";
        }
        int idx = text.indexOf("|");
        if (idx < 0) {
            return "";
        }
        return text.substring(idx + 1).trim();
    }

    private String extractUrl(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String value = text;
        int httpIdx = indexOfIgnoreCase(value, "http://");
        int httpsIdx = indexOfIgnoreCase(value, "https://");
        int wwwIdx = indexOfIgnoreCase(value, "www.");
        int start = -1;
        if (httpIdx >= 0 && httpsIdx >= 0) {
            start = Math.min(httpIdx, httpsIdx);
        } else if (httpIdx >= 0) {
            start = httpIdx;
        } else if (httpsIdx >= 0) {
            start = httpsIdx;
        } else if (wwwIdx >= 0) {
            start = wwwIdx;
        }
        if (start < 0) {
            return "";
        }
        String candidate = value.substring(start).trim();
        int end = candidate.length();
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isWhitespace(candidate.charAt(i))) {
                end = i;
                break;
            }
        }
        candidate = candidate.substring(0, end);
        candidate = candidate.replaceAll("[).,;]+$", "");
        if (candidate.startsWith("www.")) {
            return "https://" + candidate;
        }
        return candidate;
    }

    private int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String deriveTargetText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutValue = removeQuoted(text);
        String candidate = substringAfterAny(withoutValue, new String[]{" into ", " in ", " from ", " on ", " of "});
        if (candidate.isBlank()) {
            candidate = stripLeadingVerbs(withoutValue);
        }
        candidate = candidate.replaceAll("(?i)\\b(the|a|an|button|link|field|textbox|input|dropdown|menu|tab)\\b", "");
        return normalizeSpace(candidate);
    }

    private String removeQuoted(String text) {
        String value = extractQuoted(text);
        if (value.isBlank()) {
            return text;
        }
        return text.replace("\"" + value + "\"", "").replace("'" + value + "'", "");
    }

    private String substringAfterAny(String text, String[] keys) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String key : keys) {
            int idx = lower.indexOf(key);
            if (idx >= 0) {
                return text.substring(idx + key.length()).trim();
            }
        }
        return "";
    }

    private String stripLeadingVerbs(String text) {
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] verbs = new String[]{"click", "select", "choose", "pick", "verify", "see", "should see",
                "open", "navigate to", "go to", "visit", "type", "enter", "fill"};
        for (String verb : verbs) {
            if (lower.startsWith(verb + " ")) {
                return trimmed.substring(verb.length()).trim();
            }
        }
        return trimmed;
    }

    private String normalizeSpace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String resolveSeleniumLocator(String explicit, String fallback) {
        if (explicit == null || explicit.isBlank()) {
            return fallback;
        }
        String loc = explicit.trim();
        if (loc.startsWith("css=")) {
            return "By.cssSelector(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(4).trim())) + "\")";
        }
        if (loc.startsWith("xpath=")) {
            return "By.xpath(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(6).trim())) + "\")";
        }
        if (loc.startsWith("id=")) {
            return "By.id(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(3).trim())) + "\")";
        }
        if (loc.startsWith("name=")) {
            return "By.name(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(5).trim())) + "\")";
        }
        if (loc.startsWith("css:") || loc.startsWith("xpath:") || loc.startsWith("id:")
                || loc.startsWith("name:")) {
            return resolveSeleniumLocator(loc.replaceFirst(":", "="), fallback);
        }
        return "By.cssSelector(\"" + escapeJavaString(loc) + "\")";
    }

    private String resolvePlaywrightLocator(String explicit, String fallback) {
        if (explicit == null || explicit.isBlank()) {
            return fallback;
        }
        String loc = explicit.trim();
        if (loc.startsWith("css=")) {
            return "page.locator(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(4).trim())) + "\")";
        }
        if (loc.startsWith("xpath=")) {
            return "page.locator(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(6).trim())) + "\")";
        }
        if (loc.startsWith("text=")) {
            return "page.getByText(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(5).trim())) + "\")";
        }
        if (loc.startsWith("label=")) {
            return "page.getByLabel(\"" + escapeJavaString(stripSurroundingQuotes(loc.substring(6).trim())) + "\")";
        }
        if (loc.startsWith("css:") || loc.startsWith("xpath:") || loc.startsWith("text:")
                || loc.startsWith("label:")) {
            return resolvePlaywrightLocator(loc.replaceFirst(":", "="), fallback);
        }
        return "page.locator(\"" + escapeJavaString(loc) + "\")";
    }

    private String stripSurroundingQuotes(String value) {
        if (value == null) {
            return "";
        }
        String out = value.trim();
        while (out.length() >= 2) {
            boolean doubleQuoted = out.startsWith("\"") && out.endsWith("\"");
            boolean singleQuoted = out.startsWith("'") && out.endsWith("'");
            if (!doubleQuoted && !singleQuoted) {
                break;
            }
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }


    private String locatorForClickSelenium(String target) {
        if (target.isBlank() || "TODO".equalsIgnoreCase(target)) {
            return "By.cssSelector(\"TODO\")";
        }
        String literal = xpathLiteral(target);
        return "By.xpath(\"//*[self::button or self::a][normalize-space(.)=" + literal + "]\")";
    }

    private String locatorForInputSelenium(String target) {
        if (target.isBlank() || "TODO".equalsIgnoreCase(target)) {
            return "By.cssSelector(\"TODO\")";
        }
        String literal = xpathLiteral(target);
        return "By.xpath(\"//label[normalize-space(.)=" + literal + "]/following::input[1] | " +
                "//*[@placeholder=" + literal + " or @aria-label=" + literal + " or @name=" + literal + " or @id=" + literal + " or @title=" + literal + "]\")";
    }

    private String locatorForSelectSelenium(String target) {
        if (target.isBlank() || "TODO".equalsIgnoreCase(target)) {
            return "By.cssSelector(\"TODO\")";
        }
        String literal = xpathLiteral(target);
        return "By.xpath(\"//label[normalize-space(.)=" + literal + "]/following::select[1] | " +
                "//select[@name=" + literal + " or @id=" + literal + " or @aria-label=" + literal + "]\")";
    }

    private String locatorForTextSelenium(String target) {
        if (target.isBlank() || "TODO".equalsIgnoreCase(target)) {
            return "By.cssSelector(\"TODO\")";
        }
        String literal = xpathLiteral(target);
        return "By.xpath(\"//*[contains(normalize-space(.)," + literal + ")]\")";
    }

    private String xpathLiteral(String text) {
        String cleaned = text.replace("\"", "").replace("'", "");
        return "'" + cleaned + "'";
    }

    private String seleniumPageObject(List<StepDef> defs, String pageClassName) {
        StringBuilder fields = new StringBuilder();
        Set<String> usedConstants = new HashSet<>();
        for (StepDef def : defs) {
            if (isOpenStep(def.rawText)) {
                continue;
            }
            String constant = locatorConstantName(def.methodName);
            if (!usedConstants.add(constant)) {
                continue;
            }
            String locator = seleniumLocatorExpression(def.rawText);
            fields.append("public static final By ").append(constant).append(" = ").append(locator).append(";\n");
        }
        return """
                package pages;
                
                import org.openqa.selenium.By;
                
                public class %s {
                    private %s() {
                    }
                
                %s
                }
                """.formatted(pageClassName, pageClassName, indent(fields.toString(), 4));
    }

    private String seleniumLocatorExpression(String text) {
        String target = deriveTargetText(text);
        String explicitLocator = extractLocator(text);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (isBulkLinkClickStep(text)) {
            return resolveSeleniumLocator(explicitLocator, "By.tagName(\"a\")");
        }
        if (lower.contains("click")) {
            return resolveSeleniumLocator(explicitLocator, locatorForClickSelenium(target));
        }
        if (lower.contains("type") || lower.contains("enter") || lower.contains("fill")) {
            return resolveSeleniumLocator(explicitLocator, locatorForInputSelenium(target));
        }
        if (lower.contains("select")) {
            return resolveSeleniumLocator(explicitLocator, locatorForSelectSelenium(target));
        }
        if (lower.contains("wait")) {
            return resolveSeleniumLocator(explicitLocator, locatorForTextSelenium(target));
        }
        if (lower.contains("verify") || lower.contains("should") || lower.contains("see")) {
            String quoted = extractQuoted(text);
            String value = quoted.isBlank() ? (target.isBlank() ? "TODO" : target) : quoted;
            return resolveSeleniumLocator(explicitLocator, locatorForTextSelenium(value));
        }
        return resolveSeleniumLocator(explicitLocator, "By.cssSelector(\"TODO\")");
    }

    private boolean isBulkLinkClickStep(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean allLinks = (lower.contains("all links")
                || lower.contains("each link")
                || lower.contains("every link")
                || lower.contains("all anchor")
                || lower.contains("each anchor")
                || lower.contains("every anchor"));
        return allLinks && lower.contains("click");
    }

    private String playwrightPageObject(List<StepDef> defs, String pageClassName) {
        StringBuilder methods = new StringBuilder();
        for (StepDef def : defs) {
            boolean openStep = isOpenStep(def.rawText);
            String explicitUrl = extractUrl(def.rawText);
            methods.append("""
                        public void %s() {
                            withPage(page -> {
                    %s
                            });
                        }
                    
                    """.formatted(def.methodName,
                    openStep
                            ? (explicitUrl.isBlank()
                            ? "            manager.openBaseUrl();\n"
                            : "            page.navigate(\"" + escapeJavaString(explicitUrl) + "\");\n")
                            : indent(actionForPlaywright(def.rawText), 12)));
        }
        return """
                package pages;
                
                import com.microsoft.playwright.Page;
                import java.util.function.Consumer;
                import playwright.browser.PlaywrightManager;
                
                public class %s {
                    private final PlaywrightManager manager = PlaywrightManager.getInstance();
                
                    public void withPage(Consumer<Page> action) {
                        action.accept(manager.getPage());
                    }
                
                %s
                }
                """.formatted(pageClassName, indent(methods.toString(), 4));
    }

    private String playwrightContext() {
        return """
                package support;
                
                import com.microsoft.playwright.Browser;
                import com.microsoft.playwright.BrowserType;
                import com.microsoft.playwright.Page;
                import com.microsoft.playwright.Playwright;
                import java.io.InputStream;
                import java.util.Properties;
                
                public class PlaywrightContext {
                    private static final PlaywrightContext INSTANCE = new PlaywrightContext();
                    private Playwright playwright;
                    private Browser browser;
                    private Page page;
                    private final Properties props = loadProps();
                
                    public static PlaywrightContext getInstance() {
                        return INSTANCE;
                    }
                
                    public void start() {
                        if (page != null) {
                            return;
                        }
                        playwright = Playwright.create();
                        BrowserType browserType = resolveBrowser(playwright);
                        browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
                        page = browser.newPage();
                    }
                
                    public Page getPage() {
                        if (page == null) {
                            start();
                        }
                        return page;
                    }
                
                    public void stop() {
                        if (browser != null) {
                            browser.close();
                        }
                        if (playwright != null) {
                            playwright.close();
                        }
                        browser = null;
                        playwright = null;
                        page = null;
                    }
                
                    public String getBaseUrl() {
                        return props.getProperty("url", "");
                    }
                
                    private boolean isHeadless() {
                        return Boolean.parseBoolean(props.getProperty("headless", "false"));
                    }
                
                    private BrowserType resolveBrowser(Playwright playwright) {
                        String name = props.getProperty("browser", "chromium");
                        if ("firefox".equalsIgnoreCase(name)) {
                            return playwright.firefox();
                        }
                        if ("webkit".equalsIgnoreCase(name)) {
                            return playwright.webkit();
                        }
                        return playwright.chromium();
                    }
                
                    private Properties loadProps() {
                        Properties props = new Properties();
                        try (InputStream input = PlaywrightContext.class.getClassLoader()
                                .getResourceAsStream("config.properties")) {
                            if (input != null) {
                                props.load(input);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load config.properties", e);
                        }
                        return props;
                    }
                }
                """;
    }

    private String playwrightHooks() {
        return """
                package hooks;
                
                import io.cucumber.java.After;
                import io.cucumber.java.Before;
                import io.cucumber.java.Scenario;
                import support.PlaywrightContext;
                
                public class CucumberHooks {
                
                    @Before
                    public void beforeScenario(Scenario scenario) {
                        PlaywrightContext.getInstance().start();
                    }
                
                    @After
                    public void afterScenario(Scenario scenario) {
                        PlaywrightContext.getInstance().stop();
                    }
                }
                """;
    }

    private String seleniumHooks() {
        return """
                package hooks;
                
                import browsers.BrowserManager;
                import io.cucumber.java.After;
                
                public class CucumberHooks {
                
                    @After
                    public void afterScenario() {
                        BrowserManager.getInstance().quitBrowser();
                    }
                }
                """;
    }

    private String seleniumStepHelper() {
        return """
                package steps;
                
                import java.time.Duration;
                import org.openqa.selenium.By;
                import org.openqa.selenium.WebDriver;
                import org.openqa.selenium.WebElement;
                import org.openqa.selenium.support.ui.ExpectedConditions;
                import org.openqa.selenium.support.ui.Select;
                import org.openqa.selenium.support.ui.WebDriverWait;
                
                public class Step {
                    private final WebDriver driver;
                    private By element;
                
                    private Step(WebDriver driver) {
                        this.driver = driver;
                    }
                
                    public static Step create(WebDriver driver) {
                        return new Step(driver);
                    }
                
                    public Step setWebElement(By element) {
                        this.element = element;
                        return this;
                    }
                
                    public Step click() {
                        currentElement().click();
                        return this;
                    }
                
                    public Step type(String value) {
                        WebElement el = currentElement();
                        el.clear();
                        el.sendKeys(value);
                        return this;
                    }
                
                    public Step selectByVisibleText(String value) {
                        new Select(currentElement()).selectByVisibleText(value);
                        return this;
                    }
                
                    public Step waitForVisible() {
                        new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(ExpectedConditions.visibilityOfElementLocated(requiredLocator()));
                        return this;
                    }
                
                    public Step verifyTextContains(String expected) {
                        String actual = currentElement().getText();
                        if (actual == null || !actual.contains(expected)) {
                            throw new AssertionError("Expected text to contain: " + expected);
                        }
                        return this;
                    }
                
                    private By requiredLocator() {
                        if (element == null) {
                            throw new IllegalStateException("Call setWebElement(...) before executing step actions.");
                        }
                        return element;
                    }
                
                    private WebElement currentElement() {
                        return new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(ExpectedConditions.visibilityOfElementLocated(requiredLocator()));
                    }
                }
                """;
    }

    private String browserOptionsJson(GenerateRequest request) {
        List<String> chromeArgs = chromeArguments(request);
        List<String> firefoxArgs = firefoxArguments(request);
        List<String> edgeArgs = edgeArguments(request);
        String chromeArgsJson = chromeArgs.isEmpty()
                ? ""
                : chromeArgs.stream().map(this::jsonString).collect(java.util.stream.Collectors.joining(", "));
        String firefoxArgsJson = firefoxArgs.isEmpty()
                ? ""
                : firefoxArgs.stream().map(this::jsonString).collect(java.util.stream.Collectors.joining(", "));
        String edgeArgsJson = edgeArgs.isEmpty()
                ? ""
                : edgeArgs.stream().map(this::jsonString).collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                  "browsers": {
                    "chrome": {
                      "arguments": [%s],
                      "acceptInsecureCerts": %s
                    },
                    "firefox": {
                      "arguments": [%s],
                      "acceptInsecureCerts": %s
                    },
                    "edge": {
                      "arguments": [%s],
                      "acceptInsecureCerts": %s
                    }
                  }
                }
                """.formatted(
                chromeArgsJson,
                request.isChromeAcceptInsecureCerts(),
                firefoxArgsJson,
                request.isFirefoxAcceptInsecureCerts(),
                edgeArgsJson,
                request.isEdgeAcceptInsecureCerts());
    }

    private String jsonString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private List<String> chromeArguments(GenerateRequest request) {
        LinkedHashSet<String> args = new LinkedHashSet<>();
        if (request.isChromeStartMaximized()) {
            args.add("--start-maximized");
        }
        if (request.isChromeIncognito()) {
            args.add("--incognito");
        }
        if (request.isChromeDisableNotifications()) {
            args.add("--disable-notifications");
        }
        if (request.isChromeDisablePopupBlocking()) {
            args.add("--disable-popup-blocking");
        }
        String custom = request.getChromeCustomArgs();
        if (custom != null && !custom.isBlank()) {
            String[] lines = custom.replace("\r", "\n").split("\n");
            for (String line : lines) {
                String[] parts = line.split(",");
                for (String part : parts) {
                    String value = part.trim();
                    if (!value.isBlank()) {
                        args.add(value);
                    }
                }
            }
        }
        return new ArrayList<>(args);
    }

    private List<String> firefoxArguments(GenerateRequest request) {
        LinkedHashSet<String> args = new LinkedHashSet<>();
        if (request.isFirefoxPrivateMode()) {
            args.add("-private");
        }
        addCustomArgs(args, request.getFirefoxCustomArgs());
        return new ArrayList<>(args);
    }

    private List<String> edgeArguments(GenerateRequest request) {
        LinkedHashSet<String> args = new LinkedHashSet<>();
        if (request.isEdgeStartMaximized()) {
            args.add("--start-maximized");
        }
        if (request.isEdgeInPrivate()) {
            args.add("--inprivate");
        }
        addCustomArgs(args, request.getEdgeCustomArgs());
        return new ArrayList<>(args);
    }

    private void addCustomArgs(LinkedHashSet<String> args, String custom) {
        if (custom == null || custom.isBlank()) {
            return;
        }
        String[] lines = custom.replace("\r", "\n").split("\n");
        for (String line : lines) {
            String[] parts = line.split(",");
            for (String part : parts) {
                String value = part.trim();
                if (!value.isBlank()) {
                    args.add(value);
                }
            }
        }
    }

    private String seleniumBaseUrlHook() {
        return """
                package projecthooks;
                
                import browsers.BrowserManager;
                import io.cucumber.java.Before;
                import java.io.InputStream;
                import java.util.Properties;
                
                public class BaseUrlHook {
                
                    @Before
                    public void openBaseUrl() {
                        String url = loadProps().getProperty("url", "");
                        if (url != null && !url.isBlank()) {
                            BrowserManager.getInstance().open(url);
                        }
                    }
                
                    private Properties loadProps() {
                        Properties props = new Properties();
                        try (InputStream input = getClass().getClassLoader()
                                .getResourceAsStream("config.properties")) {
                            if (input != null) {
                                props.load(input);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load config.properties", e);
                        }
                        return props;
                    }
                }
                """;
    }

    private String playwrightBaseUrlHook() {
        return """
                package projecthooks;
                
                import io.cucumber.java.Before;
                import java.io.InputStream;
                import java.util.Properties;
                import support.PlaywrightContext;
                
                public class BaseUrlHook {
                
                    @Before
                    public void openBaseUrl() {
                        String url = loadProps().getProperty("url", "");
                        if (url != null && !url.isBlank()) {
                            PlaywrightContext.getInstance().getPage().navigate(url);
                        }
                    }
                
                    private Properties loadProps() {
                        Properties props = new Properties();
                        try (InputStream input = getClass().getClassLoader()
                                .getResourceAsStream("config.properties")) {
                            if (input != null) {
                                props.load(input);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load config.properties", e);
                        }
                        return props;
                    }
                }
                """;
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return "";
        }
        String cleaned = tags.replace(",", " ").trim();
        String[] parts = cleaned.split("\\s+");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (part.startsWith("@")) {
                normalized.add(part);
            } else {
                normalized.add("@" + part);
            }
        }
        return String.join(" ", normalized);
    }

    public static class GenerationResult {
        private final Path projectRoot;
        private final ScenarioData scenario;
        private final String safeName;

        public GenerationResult(Path projectRoot, ScenarioData scenario, String safeName) {
            this.projectRoot = projectRoot;
            this.scenario = scenario;
            this.safeName = safeName;
        }

        public Path getProjectRoot() {
            return projectRoot;
        }

        public ScenarioData getScenario() {
            return scenario;
        }

        public String getSafeName() {
            return safeName;
        }

        public String getFeatureFileName() {
            return scenario.featureFileName;
        }
    }

    private static class ScenarioData {
        private final String featureText;
        private final String featureFileName;
        private final List<StepDef> stepDefinitions;
        private final String stepsClassName;
        private final String pageClassName;

        private ScenarioData(String featureText, String featureFileName, List<StepDef> stepDefinitions,
                             String stepsClassName, String pageClassName) {
            this.featureText = featureText;
            this.featureFileName = featureFileName;
            this.stepDefinitions = stepDefinitions;
            this.stepsClassName = stepsClassName;
            this.pageClassName = pageClassName;
        }
    }

    private static class StepDef {
        private final String keyword;
        private final String pattern;
        private final String methodName;
        private final String rawText;

        private StepDef(String keyword, String pattern, String methodName, String rawText) {
            this.keyword = keyword;
            this.pattern = pattern;
            this.methodName = methodName;
            this.rawText = rawText;
        }
    }
}
