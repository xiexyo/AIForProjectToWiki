package com.bank.docgen.model;

import java.util.List;

public record RepoScanResult(
        String fileTree,
        List<CodeFile> files
) {
}