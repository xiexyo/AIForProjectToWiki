package com.bank.docgen.llm;

import com.bank.docgen.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AppProperties properties;

    private final WebClient webClient;

    public OpenAiCompatibleClient(AppProperties properties) {
        this.properties = properties;

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.getLlm().getApiKey() != null && !properties.getLlm().getApiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getLlm().getApiKey());
        }

        this.webClient = builder.build();

        log.info("LLM Client 初始化完成，baseUrl={}, model={}, contextWindowTokens={}, maxTokens={}",
                properties.getLlm().getBaseUrl(),
                properties.getLlm().getModel(),
                properties.getLlm().getContextWindowTokens(),
                properties.getLlm().getMaxTokens());
    }

    public String chat(String systemPrompt, String userPrompt) {
        RuntimeException lastException = null;

        for (int i = 1; i <= 3; i++) {
            try {
                log.info("开始调用大模型，第 {} 次尝试，model={}, promptChars={}",
                        i,
                        properties.getLlm().getModel(),
                        userPrompt == null ? 0 : userPrompt.length());

                long start = System.currentTimeMillis();

                String result = doChat(systemPrompt, userPrompt);

                long cost = System.currentTimeMillis() - start;

                log.info("大模型调用成功，第 {} 次尝试，responseChars={}, costMs={}",
                        i,
                        result == null ? 0 : result.length(),
                        cost);

                return result;
            } catch (RuntimeException e) {
                lastException = e;

                log.warn("大模型调用失败，第 {} 次尝试，原因={}", i, e.getMessage(), e);

                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("大模型调用被中断", interruptedException);
                }
            }
        }

        throw new RuntimeException("大模型调用失败，已重试 3 次", lastException);
    }

    private String doChat(String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", properties.getLlm().getModel(),
                "temperature", properties.getLlm().getTemperature(),
                "max_tokens", properties.getLlm().getMaxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));

        if (response == null) {
            throw new RuntimeException("大模型响应为空");
        }

        JsonNode choices = response.path("choices");

        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("大模型响应格式异常: " + response);
        }

        String content = choices.get(0)
                .path("message")
                .path("content")
                .asText();

        if (content == null || content.isBlank()) {
            throw new RuntimeException("大模型响应内容为空: " + response);
        }

        return content;
    }
}