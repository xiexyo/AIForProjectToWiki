package com.bank.docgen.controller;

import com.bank.docgen.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final AppProperties properties;

    public HealthController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("llmBaseUrl", properties.getLlm().getBaseUrl());
        result.put("llmModel", properties.getLlm().getModel());
        result.put("contextWindowTokens", properties.getLlm().getContextWindowTokens());
        result.put("workspace", properties.getWorkspace().toAbsolutePath().toString());
        return result;
    }
}