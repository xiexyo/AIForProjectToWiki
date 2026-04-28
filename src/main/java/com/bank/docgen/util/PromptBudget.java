package com.bank.docgen.util;

import com.bank.docgen.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class PromptBudget {

    private final AppProperties properties;

    public PromptBudget(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据模型上下文窗口、预留输出 token、安全余量，估算单次 prompt 最大可用字符数。
     */
    public int maxPromptChars() {
        int contextTokens = properties.getLlm().getContextWindowTokens();
        int reservedOutputTokens = properties.getLlm().getReservedOutputTokens();
        int safetyTokens = properties.getLlm().getSafetyTokens();

        int availableInputTokens = contextTokens - reservedOutputTokens - safetyTokens;

        if (availableInputTokens <= 0) {
            availableInputTokens = contextTokens / 2;
        }

        return (int) (availableInputTokens * properties.getLlm().getCharsPerToken());
    }

    public boolean exceeds(String text) {
        return text != null && text.length() > maxPromptChars();
    }

    public String limit(String text, int maxChars) {
        if (text == null) {
            return "";
        }

        if (maxChars <= 0) {
            return "";
        }

        if (text.length() <= maxChars) {
            return text;
        }

        return text.substring(0, maxChars) + "\n\n...内容过长，已按上下文预算截断...";
    }
}