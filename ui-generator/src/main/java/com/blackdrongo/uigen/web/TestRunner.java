package com.blackdrongo.uigen.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TestRunner {
    private static volatile long frameworkInstallStamp = -1L;

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
            String message = stackTrace(e);
            return new TestRunResult(1, firstLine(message), message);
        }
    }

    private static TestRunResult ensureFrameworkInstalled() throws IOException, InterruptedException {
        Path repoRoot = AppPaths.appRoot().getParent();
        if (repoRoot == null) {
            return new TestRunResult(1, "Framework root not found.", "Unable to resolve repository root.");
        }
        Path pom = repoRoot.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return new TestRunResult(1, "Framework pom not found.", pom.toAbsolutePath().toString());
        }
        long stamp = Files.getLastModifiedTime(pom).toMillis();
        Path installedJar = localMavenRepoJar();
        if (frameworkInstallStamp == stamp && Files.exists(installedJar)) {
            return null;
        }
        List<String> command = new ArrayList<>(baseMavenCommand());
        command.add("-q");
        command.add("-DskipTests");
        command.add("install");
        ProcessResult result = runProcess(command, repoRoot);
        if (result.exitCode != 0) {
            String output = result.output.isBlank() ? "Failed to install BlackDrongo framework." : result.output;
            return new TestRunResult(result.exitCode, firstMeaningfulLine(output), output);
        }
        frameworkInstallStamp = stamp;
        return null;
    }

    private static TestRunResult runWithMaven(Path projectRoot, String tags, String mvnArgs,
                                              String scenarioName, Path featuresPath)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(baseMavenCommand());
        command.add("-q");
        command.addAll(systemPropertyArgs(tags, mvnArgs, scenarioName, featuresPath));
        command.add("test");
        ProcessResult result = runProcess(command, projectRoot);
        String output = sanitizeMavenOutput(result.output);
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
                args.add(token);
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

    private static Path localMavenRepoJar() {
        String userHome = System.getProperty("user.home", "");
        return Path.of(userHome, ".m2", "repository", "BlackDrongo", "BlackDrongo",
                "0.0.1-SNAPSHOT", "BlackDrongo-0.0.1-SNAPSHOT.jar");
    }

    private static String firstMeaningfulLine(String text) {
        if (text == null || text.isBlank()) {
            return "Tests failed.";
        }
        String[] lines = text.replace("\r", "").split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("[INFO]")) {
                continue;
            }
            return trimmed;
        }
        return firstLine(text);
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

    private record ProcessResult(int exitCode, String output) {}
}
