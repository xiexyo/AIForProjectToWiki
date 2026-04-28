package com.bank.docgen.service;

import com.bank.docgen.config.AppProperties;
import com.bank.docgen.model.UploadResult;
import com.bank.docgen.util.ZipUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectWorkspaceService {

    private final AppProperties properties;

    public ProjectWorkspaceService(AppProperties properties) {
        this.properties = properties;
    }

    public UploadResult createFromZip(MultipartFile file) throws IOException {
        String projectId = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        Path baseDir = baseDir(projectId);
        Path sourceDir = sourceDir(projectId);
        Path outputDir = outputDir(projectId);

        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);

        Path zipFile = baseDir.resolve("upload.zip");
        file.transferTo(zipFile);

        ZipUtil.unzip(zipFile, sourceDir);

        return new UploadResult(
                projectId,
                sourceDir.toAbsolutePath().toString(),
                outputDir.toAbsolutePath().toString()
        );
    }

    public Path baseDir(String projectId) {
        return properties.getWorkspace()
                .resolve(projectId)
                .normalize();
    }

    public Path sourceDir(String projectId) {
        return baseDir(projectId)
                .resolve("source")
                .normalize();
    }

    public Path outputDir(String projectId) {
        return baseDir(projectId)
                .resolve("generated-docs")
                .normalize();
    }

    public List<String> listGeneratedFiles(String projectId) throws IOException {
        Path outputDir = outputDir(projectId);

        if (!Files.exists(outputDir)) {
            return List.of();
        }

        return Files.walk(outputDir)
                .filter(Files::isRegularFile)
                .map(path -> outputDir.relativize(path)
                        .toString()
                        .replace("\\", "/"))
                .sorted()
                .toList();
    }

    public String readGeneratedFile(String projectId, String relativePath) throws IOException {
        Path outputDir = outputDir(projectId);
        Path file = outputDir.resolve(relativePath).normalize();

        if (!file.startsWith(outputDir)) {
            throw new IOException("非法文件路径: " + relativePath);
        }

        if (!Files.exists(file)) {
            throw new IOException("文件不存在: " + relativePath);
        }

        return Files.readString(file);
    }

    public void cleanProject(String projectId) throws IOException {
        Path base = baseDir(projectId);

        if (!Files.exists(base)) {
            return;
        }

        Files.walk(base)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
    }
}