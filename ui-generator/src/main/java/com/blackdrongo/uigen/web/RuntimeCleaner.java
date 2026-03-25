package com.blackdrongo.uigen.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeCleaner {
    private RuntimeCleaner() {
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((left, right) -> right.compareTo(left)).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + current, e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
