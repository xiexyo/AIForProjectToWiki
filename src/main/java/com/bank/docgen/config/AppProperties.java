package com.bank.docgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "docgen")
public class AppProperties {

    private Path workspace = Paths.get("./workspace");

    private Llm llm = new Llm();

    private Scan scan = new Scan();

    private Generation generation = new Generation();

    public Path getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Path workspace) {
        this.workspace = workspace;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Scan getScan() {
        return scan;
    }

    public void setScan(Scan scan) {
        this.scan = scan;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public static class Llm {

        private String baseUrl;

        private String apiKey;

        private String model;

        private double temperature = 0.1;

        private int timeoutSeconds = 300;

        private int maxTokens = 4096;

        /**
         * 模型上下文窗口。
         * 需要和 GLM 推理服务的 max_model_len 对齐。
         */
        private int contextWindowTokens = 32768;

        /**
         * 给输出预留 token。
         */
        private int reservedOutputTokens = 4096;

        /**
         * 安全余量，避免压线导致服务端报上下文超限。
         */
        private int safetyTokens = 2048;

        /**
         * 粗略估算字符/token。
         * 中文 + 代码混合场景建议保守一点。
         */
        private double charsPerToken = 1.2;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getContextWindowTokens() {
            return contextWindowTokens;
        }

        public void setContextWindowTokens(int contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
        }

        public int getReservedOutputTokens() {
            return reservedOutputTokens;
        }

        public void setReservedOutputTokens(int reservedOutputTokens) {
            this.reservedOutputTokens = reservedOutputTokens;
        }

        public int getSafetyTokens() {
            return safetyTokens;
        }

        public void setSafetyTokens(int safetyTokens) {
            this.safetyTokens = safetyTokens;
        }

        public double getCharsPerToken() {
            return charsPerToken;
        }

        public void setCharsPerToken(double charsPerToken) {
            this.charsPerToken = charsPerToken;
        }
    }

    public static class Scan {

        /**
         * 单文件最大读取字符数。
         */
        private int maxFileChars = 12000;

        /**
         * 单个源码 chunk 字符数。
         */
        private int chunkChars = 22000;

        public int getMaxFileChars() {
            return maxFileChars;
        }

        public void setMaxFileChars(int maxFileChars) {
            this.maxFileChars = maxFileChars;
        }

        public int getChunkChars() {
            return chunkChars;
        }

        public void setChunkChars(int chunkChars) {
            this.chunkChars = chunkChars;
        }
    }

    public static class Generation {

        /**
         * 单个 chunk 分析结果摘要最大字符数。
         */
        private int maxSummaryChars = 6000;

        /**
         * 每次 merge 输入的最大字符数。
         */
        private int mergeInputChars = 24000;

        /**
         * 生成单个文档时传给模型的知识字符上限。
         */
        private int docKnowledgeChars = 24000;

        public int getMaxSummaryChars() {
            return maxSummaryChars;
        }

        public void setMaxSummaryChars(int maxSummaryChars) {
            this.maxSummaryChars = maxSummaryChars;
        }

        public int getMergeInputChars() {
            return mergeInputChars;
        }

        public void setMergeInputChars(int mergeInputChars) {
            this.mergeInputChars = mergeInputChars;
        }

        public int getDocKnowledgeChars() {
            return docKnowledgeChars;
        }

        public void setDocKnowledgeChars(int docKnowledgeChars) {
            this.docKnowledgeChars = docKnowledgeChars;
        }
    }
}