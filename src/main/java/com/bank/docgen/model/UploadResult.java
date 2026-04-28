package com.bank.docgen.model;

public record UploadResult(
        String projectId,
        String sourceDir,
        String outputDir
) {
}