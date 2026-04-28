package com.bank.docgen.scanner;

import com.bank.docgen.config.AppProperties;
import com.bank.docgen.model.CodeFile;
import com.bank.docgen.model.RepoScanResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class SourceScanner {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git",
            ".idea",
            ".vscode",
            "target",
            "build",
            "out",
            "node_modules",
            "logs",
            "log",
            ".mvn",
            ".gradle"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".java",
            ".xml",
            ".yml",
            ".yaml",
            ".properties",
            ".sql",
            ".md",
            ".json",
            ".txt"
    );

    private final AppProperties properties;

    public SourceScanner(AppProperties properties) {
        this.properties = properties;
    }

    public RepoScanResult scan(Path sourceDir) throws IOException {
        List<CodeFile> files = new ArrayList<>();
        List<String> treeLines = new ArrayList<>();

        if (!Files.exists(sourceDir)) {
            throw new IOException("源码目录不存在: " + sourceDir);
        }

        Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Path relative = sourceDir.relativize(path);
                        String relativeText = relative.toString().replace("\\", "/");

                        if (shouldSkip(relative)) {
                            return;
                        }

                        String extension = extensionOf(relativeText);
                        if (!ALLOWED_EXTENSIONS.contains(extension)) {
                            return;
                        }

                        String content = Files.readString(path);

                        int maxChars = properties.getScan().getMaxFileChars();
                        if (content.length() > maxChars) {
                            content = content.substring(0, maxChars)
                                    + "\n\n/* 文件内容过长，已截断，仅用于模型分析 */";
                        }

                        treeLines.add(relativeText);
                        files.add(new CodeFile(relativeText, extension, content));

                    } catch (Exception ignored) {
                        // 单文件读取失败不影响整体扫描
                    }
                });

        Collections.sort(treeLines);
        files.sort(Comparator.comparing(CodeFile::path));

        String fileTree = String.join("\n", treeLines);

        return new RepoScanResult(fileTree, files);
    }

    private boolean shouldSkip(Path relative) {
        for (Path part : relative) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private String extensionOf(String path) {
        int index = path.lastIndexOf(".");
        if (index < 0) {
            return "";
        }
        return path.substring(index).toLowerCase();
    }
}