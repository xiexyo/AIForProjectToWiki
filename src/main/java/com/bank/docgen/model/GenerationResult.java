package com.bank.docgen.model;

import java.util.List;

public record GenerationResult(
        String projectId,
        String outputDir,
        List<String> generatedFiles
) {
}