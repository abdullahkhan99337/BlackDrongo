package com.blackdrongo.uigen.web;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

final class AppPaths {
    private static final Path APP_ROOT = resolveAppRoot();

    private AppPaths() {
    }

    static Path appRoot() {
        return APP_ROOT;
    }

    static Path generatedDir() {
        return APP_ROOT.resolve("generated");
    }

    static Path dataDir() {
        return APP_ROOT.resolve("data");
    }

    private static Path resolveAppRoot() {
        try {
            URL location = AppPaths.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equalsIgnoreCase(location.getProtocol())) {
                Path source = Path.of(location.toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(source) && source.toString().endsWith(".jar")) {
                    Path targetDir = source.getParent();
                    if (targetDir != null && targetDir.getFileName() != null
                            && "target".equalsIgnoreCase(targetDir.getFileName().toString())) {
                        Path root = targetDir.getParent();
                        if (root != null) {
                            return root;
                        }
                    }
                    if (targetDir != null) {
                        return targetDir;
                    }
                }
                if (Files.isDirectory(source)) {
                    Path current = source;
                    while (current != null) {
                        if (current.getFileName() != null
                                && "target".equalsIgnoreCase(current.getFileName().toString())) {
                            Path root = current.getParent();
                            if (root != null) {
                                return root;
                            }
                        }
                        current = current.getParent();
                    }
                    return source;
                }
            }
        } catch (Exception ignored) {
            // fall back to repository layout checks below
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path nested = cwd.resolve("ui-generator");
        if (Files.exists(nested)) {
            return nested;
        }
        return cwd;
    }
}
