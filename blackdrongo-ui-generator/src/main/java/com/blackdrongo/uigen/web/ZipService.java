package com.blackdrongo.uigen.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipService {

    public static Path zipProject(Path projectRoot, String safeName) throws IOException {
        Path zipPath = projectRoot.getParent().resolve(safeName + ".zip");
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            Files.walk(projectRoot).forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        return;
                    }
                    String entryName = projectRoot.relativize(path).toString().replace("\\", "/");
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return zipPath;
    }
}
