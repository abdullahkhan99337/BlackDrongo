package com.blackdrongo.uigen.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TestRunner {
    private static final int MAX_OUTPUT_LENGTH = 4000;
    private static final Set<String> BLOCKED_MAVEN_PROPS = Set.of(
            "maven.home",
            "maven.multiModuleProjectDirectory",
            "maven.repo.local",
            "maven.ext.class.path",
            "classworlds.conf",
            "user.home",
            "java.home"
    );
    private static volatile long seleniumFrameworkInstallStamp = Long.MIN_VALUE;
    private static volatile long playwrightFrameworkInstallStamp = Long.MIN_VALUE;

    public static TestRunResult run(Path projectRoot, String tags, String mvnArgs) {
        return run(projectRoot, tags, mvnArgs, null, null);
    }

    public static TestRunResult run(Path projectRoot, String tags, String mvnArgs, String scenarioName, Path featuresPath) {
        try {
            TestRunResult installResult = ensureFrameworkInstalled();
            if (installResult != null) {
                return installResult;
            }
            return runWithMaven(projectRoot, tags, mvnArgs, scenarioName, featuresPath);
        } catch (Exception e) {
            String message = sanitizeSensitiveOutput(stackTrace(e));
            return new TestRunResult(1, "Internal error while executing tests.", message);
        }
    }

    private static TestRunResult ensureFrameworkInstalled() throws IOException, InterruptedException {
        Path repoRoot = AppPaths.appRoot().getParent();
        if (repoRoot == null) {
            return new TestRunResult(1, "Framework root not found.", "Unable to resolve repository root.");
        }
        TestRunResult seleniumInstall = installFrameworkIfNeeded(
                repoRoot.resolve("blackdrongo-selenium"),
                localMavenRepoJar("com", "blackdrongo", "blackdrongo-selenium", "0.0.1-SNAPSHOT",
                        "blackdrongo-selenium-0.0.1-SNAPSHOT.jar"),
                true
        );
        if (seleniumInstall != null) {
            return seleniumInstall;
        }
        return installFrameworkIfNeeded(
                repoRoot.resolve("blackdrongo-playwright"),
                localMavenRepoJar("com", "blackdrongo", "blackdrongo-playwright", "0.0.1-SNAPSHOT",
                        "blackdrongo-playwright-0.0.1-SNAPSHOT.jar"),
                false
        );
    }

    private static TestRunResult runWithMaven(Path projectRoot, String tags, String mvnArgs,
                                              String scenarioName, Path featuresPath)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(baseMavenCommand());
        command.add("-q");
        command.addAll(systemPropertyArgs(tags, mvnArgs, scenarioName, featuresPath));
        command.add("test");
        ProcessResult result = runProcess(command, projectRoot);
        String output = sanitizeSensitiveOutput(sanitizeMavenOutput(result.output));
        if (result.exitCode == 0) {
            String success = output.isBlank() ? "Tests passed." : output;
            return new TestRunResult(0, "Tests passed.", success);
        }
        String failure = output.isBlank() ? "Generated test execution failed." : output;
        return new TestRunResult(result.exitCode, firstMeaningfulLine(failure), failure);
    }

    private static List<String> systemPropertyArgs(String tags, String mvnArgs, String scenarioName, Path featuresPath) {
        List<String> args = new ArrayList<>();
        String normalizedTags = normalizeTags(tags);
        if (!normalizedTags.isBlank()) {
            args.add("-Dcucumber.filter.tags=" + normalizedTags);
        }
        if (scenarioName != null && !scenarioName.isBlank()) {
            args.add("-Dcucumber.filter.name=^" + Pattern.quote(scenarioName.trim()) + "$");
        }
        if (featuresPath != null && Files.exists(featuresPath)) {
            args.add("-Dcucumber.features=" + featuresPath.toAbsolutePath());
        }
        for (String token : splitArgs(mvnArgs)) {
            if (!token.isBlank()) {
                args.add(sanitizeMavenArg(token));
            }
        }
        return args;
    }

    private static List<String> baseMavenCommand() {
        String mavenHome = firstNonBlank(System.getenv("MAVEN_HOME"), System.getenv("M2_HOME"));
        if (mavenHome != null) {
            Path mvnCmd = Path.of(mavenHome, "bin", isWindows() ? "mvn.cmd" : "mvn");
            if (Files.exists(mvnCmd)) {
                return List.of(mvnCmd.toString());
            }
        }
        if (isWindows()) {
            return List.of("cmd", "/c", "mvn");
        }
        return List.of("mvn");
    }

    private static ProcessResult runProcess(List<String> command, Path workDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private static String sanitizeMavenOutput(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String[] lines = output.replace("\r", "").split("\n");
        List<String> important = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("[INFO] Downloading")
                    || trimmed.startsWith("[INFO] Downloaded")
                    || trimmed.startsWith("[INFO] Scanning for projects")) {
                continue;
            }
            important.add(trimmed);
        }
        return String.join("\n", important);
    }

    private static String normalizeTags(String tags) {
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

    private static List<String> splitArgs(String input) {
        List<String> args = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }

    private static TestRunResult installFrameworkIfNeeded(Path frameworkRoot, Path installedJar, boolean required)
            throws IOException, InterruptedException {
        Path pom = frameworkRoot.resolve("pom.xml");
        if (!Files.exists(pom)) {
            if (required) {
                return new TestRunResult(1, "Framework pom not found.", pom.toAbsolutePath().toString());
            }
            return null;
        }
        long stamp = frameworkContentStamp(frameworkRoot);
        if (isFrameworkUpToDate(frameworkRoot, stamp, installedJar)) {
            return null;
        }
        List<String> command = new ArrayList<>(baseMavenCommand());
        command.add("-q");
        command.add("-DskipTests");
        command.add("install");
        ProcessResult result = runProcess(command, frameworkRoot);
        if (result.exitCode != 0) {
            String output = result.output.isBlank() ? "Failed to install framework: " + frameworkRoot.getFileName()
                    : result.output;
            return new TestRunResult(result.exitCode, firstMeaningfulLine(output), output);
        }
        updateFrameworkStamp(frameworkRoot, stamp);
        return null;
    }

    private static boolean isFrameworkUpToDate(Path frameworkRoot, long stamp, Path installedJar) {
        if (!Files.exists(installedJar)) {
            return false;
        }
        String name = frameworkRoot.getFileName().toString();
        if ("blackdrongo-selenium".equalsIgnoreCase(name)) {
            return seleniumFrameworkInstallStamp == stamp;
        }
        if ("blackdrongo-playwright".equalsIgnoreCase(name)) {
            return playwrightFrameworkInstallStamp == stamp;
        }
        return false;
    }

    private static void updateFrameworkStamp(Path frameworkRoot, long stamp) {
        String name = frameworkRoot.getFileName().toString();
        if ("blackdrongo-selenium".equalsIgnoreCase(name)) {
            seleniumFrameworkInstallStamp = stamp;
        } else if ("blackdrongo-playwright".equalsIgnoreCase(name)) {
            playwrightFrameworkInstallStamp = stamp;
        }
    }

    private static long frameworkContentStamp(Path frameworkRoot) throws IOException {
        long stamp = 0L;
        List<Path> roots = List.of(
                frameworkRoot.resolve("pom.xml"),
                frameworkRoot.resolve("src"),
                frameworkRoot.resolve("testng.xml")
        );
        for (Path root : roots) {
            stamp = 31L * stamp + pathStamp(root);
        }
        return stamp;
    }

    private static long pathStamp(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return 0L;
        }
        if (Files.isRegularFile(path)) {
            return fileStamp(path);
        }
        long stamp = 1L;
        try (var walk = Files.walk(path)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(current -> path.relativize(current).toString()))
                    .toList();
            for (Path file : files) {
                stamp = 31L * stamp + fileStamp(file);
            }
        }
        return stamp;
    }

    private static long fileStamp(Path file) throws IOException {
        long modified = Files.getLastModifiedTime(file).toMillis();
        long size = Files.size(file);
        return 31L * modified + size;
    }

    private static Path localMavenRepoJar(String... pathParts) {
        String userHome = System.getProperty("user.home", "");
        String[] fullPath = new String[pathParts.length + 2];
        fullPath[0] = ".m2";
        fullPath[1] = "repository";
        System.arraycopy(pathParts, 0, fullPath, 2, pathParts.length);
        return Path.of(userHome, fullPath);
    }

    private static String firstMeaningfulLine(String text) {
        if (text == null || text.isBlank()) {
            return "Tests failed.";
        }
        String[] lines = text.replace("\r", "").split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Caused by:")) {
                return cleanMessagePrefix(trimmed);
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || isNoiseLine(trimmed)) {
                continue;
            }
            if (looksLikeException(trimmed)) {
                return cleanMessagePrefix(trimmed);
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[ERROR]")) {
                if (isSummaryErrorLine(trimmed)) {
                    continue;
                }
                return cleanMessagePrefix(trimmed);
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Failed scenarios:")) {
                return trimmed;
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (isNoiseLine(trimmed)) {
                continue;
            }
            return cleanMessagePrefix(trimmed);
        }
        return firstLine(text);
    }

    public static String safeMessage(String message) {
        return firstMeaningfulLine(sanitizeSensitiveOutput(message));
    }

    private static String sanitizeMavenArg(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Empty Maven argument is not allowed.");
        }
        String value = token.trim();
        if (value.startsWith("-D")) {
            int equals = value.indexOf('=');
            if (equals <= 2 || equals == value.length() - 1) {
                throw new IllegalArgumentException("Only -Dkey=value format is allowed for Maven arguments.");
            }
            String key = value.substring(2, equals).trim();
            if (!key.matches("[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid Maven system property: " + key);
            }
            if (BLOCKED_MAVEN_PROPS.contains(key)) {
                throw new IllegalArgumentException("Blocked Maven property: " + key);
            }
            String propValue = value.substring(equals + 1);
            if (propValue.contains("\n") || propValue.contains("\r")) {
                throw new IllegalArgumentException("Invalid Maven property value for: " + key);
            }
            return value;
        }
        if (value.matches("-P[a-zA-Z0-9_,.-]+")) {
            return value;
        }
        if (value.matches("-T\\d+[cC]?")) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported Maven argument: " + value
                + ". Allowed: -Dkey=value, -Pprofile, -Tn.");
    }

    private static String sanitizeSensitiveOutput(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String sanitized = input;
        sanitized = sanitized.replaceAll("(?i)[A-Z]:\\\\[^\\s\\r\\n]+", "<path>");
        sanitized = sanitized.replaceAll("(?m)(^|\\s)/(?:[A-Za-z0-9._-]+/)+[A-Za-z0-9._-]+", "$1<path>");
        if (sanitized.length() > MAX_OUTPUT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_OUTPUT_LENGTH) + "\n... (truncated)";
        }
        return sanitized;
    }

    private static boolean isNoiseLine(String line) {
        if (line.startsWith("[INFO]")) {
            return true;
        }
        if (line.startsWith("WARNING:")) {
            return true;
        }
        if (line.startsWith("SLF4J:")) {
            return true;
        }
        if (line.startsWith("Picked up JAVA_TOOL_OPTIONS:")) {
            return true;
        }
        return line.startsWith("Apr ") && line.contains("WARNING:");
    }

    private static boolean looksLikeException(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("exception:") || lower.contains("assertionerror");
    }

    private static boolean isSummaryErrorLine(String line) {
        String cleaned = cleanMessagePrefix(line).toLowerCase(Locale.ROOT);
        return cleaned.startsWith("tests run:")
                || cleaned.startsWith("failures:")
                || cleaned.startsWith("errors:")
                || cleaned.startsWith("skipped:")
                || cleaned.startsWith("please refer to")
                || cleaned.startsWith("-> [help");
    }

    private static String cleanMessagePrefix(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = line.trim();
        if (cleaned.startsWith("[ERROR]")) {
            cleaned = cleaned.substring(7).trim();
        }
        return cleaned;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "Tests failed.";
        }
        String trimmed = text.trim();
        int idx = trimmed.indexOf('\n');
        if (idx >= 0) {
            return trimmed.substring(0, idx).trim();
        }
        return trimmed;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
