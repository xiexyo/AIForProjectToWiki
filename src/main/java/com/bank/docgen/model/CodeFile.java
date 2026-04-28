package com.bank.docgen.model;

public record CodeFile(
        String path,
        String extension,
        String content
) {
}