package com.bank.docgen.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtil {

    public static void unzip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();

                // 防止 Zip Slip 漏洞
                if (!outputPath.startsWith(targetDir)) {
                    throw new IOException("非法 ZIP 路径: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath);
                }

                zipInputStream.closeEntry();
            }
        }
    }
}