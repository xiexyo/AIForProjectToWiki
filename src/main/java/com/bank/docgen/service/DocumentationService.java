package com.bank.docgen.service;

import com.bank.docgen.config.AppProperties;
import com.bank.docgen.llm.OpenAiCompatibleClient;
import com.bank.docgen.model.CodeFile;
import com.bank.docgen.model.GenerationResult;
import com.bank.docgen.model.RepoScanResult;
import com.bank.docgen.prompt.PromptBuilder;
import com.bank.docgen.scanner.SourceScanner;
import com.bank.docgen.util.PromptBudget;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class DocumentationService {

    private final SourceScanner sourceScanner;

    private final OpenAiCompatibleClient llmClient;

    private final ProjectWorkspaceService workspaceService;

    private final AppProperties properties;

    private final PromptBudget promptBudget;
    private static final Logger log = LoggerFactory.getLogger(DocumentationService.class);
    public DocumentationService(SourceScanner sourceScanner,
                                OpenAiCompatibleClient llmClient,
                                ProjectWorkspaceService workspaceService,
                                AppProperties properties,
                                PromptBudget promptBudget) {
        this.sourceScanner = sourceScanner;
        this.llmClient = llmClient;
        this.workspaceService = workspaceService;
        this.properties = properties;
        this.promptBudget = promptBudget;
    }

    public GenerationResult generate(String projectId) throws IOException {
        long totalStart = System.currentTimeMillis();

        log.info("开始生成文档，projectId={}", projectId);

        Path sourceDir = workspaceService.sourceDir(projectId);
        Path outputDir = workspaceService.outputDir(projectId);

        log.info("源码目录={}, 输出目录={}", sourceDir.toAbsolutePath(), outputDir.toAbsolutePath());

        Files.createDirectories(outputDir);

        log.info("开始扫描源码，projectId={}", projectId);
        RepoScanResult scanResult = sourceScanner.scan(sourceDir);
        log.info("源码扫描完成，projectId={}, fileCount={}, fileTreeChars={}",
                projectId,
                scanResult.files().size(),
                scanResult.fileTree() == null ? 0 : scanResult.fileTree().length());

        List<String> chunks = buildChunks(scanResult.files());
        log.info("源码分片完成，projectId={}, chunkCount={}", projectId, chunks.size());

        List<String> chunkSummaries = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            int chunkNo = i + 1;

            log.info("开始分析源码分片，projectId={}, chunk={}/{}, chunkChars={}",
                    projectId,
                    chunkNo,
                    chunks.size(),
                    chunks.get(i).length());

            String prompt = PromptBuilder.chunkAnalysisPrompt(
                    scanResult.fileTree(),
                    chunks.get(i),
                    properties.getGeneration().getMaxSummaryChars()
            );

            prompt = promptBudget.limit(prompt, promptBudget.maxPromptChars());

            log.debug("源码分片 prompt 构建完成，projectId={}, chunk={}/{}, promptChars={}",
                    projectId,
                    chunkNo,
                    chunks.size(),
                    prompt.length());

            String summary;

            long chunkStart = System.currentTimeMillis();

            try {
                summary = llmClient.chat(PromptBuilder.systemPrompt(), prompt);
                summary = promptBudget.limit(
                        summary,
                        properties.getGeneration().getMaxSummaryChars()
                );

                log.info("源码分片分析成功，projectId={}, chunk={}/{}, summaryChars={}, costMs={}",
                        projectId,
                        chunkNo,
                        chunks.size(),
                        summary.length(),
                        System.currentTimeMillis() - chunkStart);

            } catch (Exception e) {
                log.error("源码分片分析失败，projectId={}, chunk={}/{}, error={}",
                        projectId,
                        chunkNo,
                        chunks.size(),
                        e.getMessage(),
                        e);

                summary = """
                    # 源码片段分析失败
                    
                    本片段调用大模型失败。
                    
                    失败原因：
                    %s
                    
                    处理建议：
                    - 可降低 scan.chunk-chars 后重试
                    - 可检查该片段是否存在超大 XML/SQL/JSON
                    - 可单独分析 metadata/chunks 对应片段
                    """.formatted(e.getMessage());
            }

            chunkSummaries.add("## Chunk " + chunkNo + "\n\n" + summary);

            writeFile(
                    outputDir,
                    "metadata/chunks/chunk-" + chunkNo + ".md",
                    summary
            );

            log.info("源码分片摘要已写入，projectId={}, chunk={}/{}", projectId, chunkNo, chunks.size());
        }

        log.info("开始分层合并摘要，projectId={}, summaryCount={}", projectId, chunkSummaries.size());

        long mergeStart = System.currentTimeMillis();

        String baselineSummary = hierarchicalMerge(chunkSummaries, outputDir);

        log.info("分层合并摘要完成，projectId={}, baselineSummaryChars={}, costMs={}",
                projectId,
                baselineSummary == null ? 0 : baselineSummary.length(),
                System.currentTimeMillis() - mergeStart);

        writeFile(outputDir, "metadata/baseline-summary.md", baselineSummary);

        Map<String, String> docTasks = buildDocTasks();

        List<String> generatedFiles = new ArrayList<>();
        generatedFiles.add("metadata/baseline-summary.md");

        int docIndex = 0;

        for (Map.Entry<String, String> task : docTasks.entrySet()) {
            docIndex++;

            String path = task.getKey();
            String goal = task.getValue();

            log.info("开始生成文档，projectId={}, doc={}/{}, path={}",
                    projectId,
                    docIndex,
                    docTasks.size(),
                    path);

            String docKnowledge = buildDocKnowledge(
                    path,
                    goal,
                    baselineSummary,
                    chunkSummaries
            );

            String prompt = PromptBuilder.docPrompt(
                    path,
                    goal,
                    promptBudget.limit(scanResult.fileTree(), 12000),
                    docKnowledge
            );

            prompt = promptBudget.limit(prompt, promptBudget.maxPromptChars());

            log.debug("文档 prompt 构建完成，projectId={}, path={}, promptChars={}",
                    projectId,
                    path,
                    prompt.length());

            String content;

            long docStart = System.currentTimeMillis();

            try {
                content = llmClient.chat(PromptBuilder.systemPrompt(), prompt);

                log.info("文档生成成功，projectId={}, path={}, contentChars={}, costMs={}",
                        projectId,
                        path,
                        content == null ? 0 : content.length(),
                        System.currentTimeMillis() - docStart);

            } catch (Exception e) {
                log.error("文档生成失败，projectId={}, path={}, error={}",
                        projectId,
                        path,
                        e.getMessage(),
                        e);

                content = """
                    # 文档生成失败
                    
                    文件路径：%s
                    
                    失败原因：
                    
                    %s
                    
                    建议：
                    - 降低 generation.doc-knowledge-chars
                    - 降低 scan.chunk-chars
                    - 检查大模型服务上下文窗口配置
                    """.formatted(path, e.getMessage());
            }

            writeFile(outputDir, path, content);
            generatedFiles.add(path);

            log.info("文档已写入，projectId={}, path={}", projectId, path);
        }

        log.info("全部文档生成完成，projectId={}, generatedFileCount={}, totalCostMs={}",
                projectId,
                generatedFiles.size(),
                System.currentTimeMillis() - totalStart);

        return new GenerationResult(
                projectId,
                outputDir.toAbsolutePath().toString(),
                generatedFiles
        );
    }
    /**
     * 构建源码分片。
     */
    private List<String> buildChunks(List<CodeFile> files) {
        int configuredChunkChars = properties.getScan().getChunkChars();

        int safeChunkChars = Math.min(
                configuredChunkChars,
                Math.max(8000, promptBudget.maxPromptChars() - 10000)
        );

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (CodeFile file : files) {
            String language = file.extension()
                    .replace(".", "")
                    .toLowerCase();

            String block = "\n\n### FILE: " + file.path() + "\n"
                    + "```" + language + "\n"
                    + file.content()
                    + "\n```\n";

            if (current.length() + block.length() > safeChunkChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }

            if (block.length() > safeChunkChars) {
                chunks.add(block.substring(0, safeChunkChars)
                        + "\n\n...单文件过长，已截断...");
            } else {
                current.append(block);
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    /**
     * 分层合并摘要，避免一次性把所有 chunk summary 塞给模型。
     */
    private String hierarchicalMerge(List<String> summaries, Path outputDir) throws IOException {
        if (summaries == null || summaries.isEmpty()) {
            return """
                    # 系统基线知识摘要
                    
                    未扫描到可分析源码文件。
                    """;
        }

        List<String> current = new ArrayList<>(summaries);

        int round = 1;
        int mergeInputChars = properties.getGeneration().getMergeInputChars();

        while (current.size() > 1) {
            List<List<String>> groups = groupByCharBudget(current, mergeInputChars);

            List<String> next = new ArrayList<>();

            for (int i = 0; i < groups.size(); i++) {
                String groupText = String.join("\n\n", groups.get(i));

                String prompt = """
                        下面是一组源码切片分析摘要，请进行去重、压缩、合并。
                        
                        要求：
                        1. 输出不要超过 %s 字。
                        2. 以业务能力/交易场景为主线。
                        3. 保留类名、方法名、接口路径、表名、TAE 场景码等证据。
                        4. 不确定的信息标注“待确认”。
                        5. 不要编造源码中没有的信息。
                        
                        输出结构：
                        
                        # 合并摘要
                        
                        ## 1. 业务能力
                        ## 2. 入口
                        ## 3. 主流程组件
                        ## 4. TAE 相关
                        ## 5. 数据模型
                        ## 6. 状态机、幂等、补偿、冲正、对账
                        ## 7. 扩展点
                        ## 8. 待确认
                        
                        ===== 待合并摘要 =====
                        %s
                        """.formatted(
                        properties.getGeneration().getMaxSummaryChars(),
                        groupText
                );

                prompt = promptBudget.limit(prompt, promptBudget.maxPromptChars());

                String merged;

                try {
                    merged = llmClient.chat(PromptBuilder.systemPrompt(), prompt);
                    merged = promptBudget.limit(
                            merged,
                            properties.getGeneration().getMaxSummaryChars()
                    );
                } catch (Exception e) {
                    merged = """
                            # 合并摘要失败
                            
                            本组摘要合并失败。
                            
                            失败原因：
                            %s
                            
                            已保留原始摘要片段的截断内容：
                            
                            %s
                            """.formatted(
                            e.getMessage(),
                            promptBudget.limit(groupText, properties.getGeneration().getMaxSummaryChars())
                    );
                }

                next.add("## Round " + round + " Group " + (i + 1) + "\n\n" + merged);

                writeFile(
                        outputDir,
                        "metadata/merge/round-" + round + "-group-" + (i + 1) + ".md",
                        merged
                );
            }

            current = next;
            round++;
        }

        String finalSummary = current.get(0);

        String finalPrompt = """
                请基于下面的合并摘要，生成最终“系统基线知识摘要”。
                
                要求：
                1. 以业务能力/交易场景为主线。
                2. 明确入口、主流程 Service、规则层、TAE、数据模型、状态机、扩展点。
                3. 资金类交易重点说明幂等、事务、补偿、冲正、对账。
                4. 保留代码路径、类名、方法名、表名。
                5. 不确定的信息标注“待确认”。
                
                输出结构：
                
                # 系统基线知识摘要
                
                ## 1. 系统总体判断
                ## 2. 业务能力清单
                ## 3. 入口层清单
                ## 4. 应用服务层/主流程清单
                ## 5. 规则层/策略层清单
                ## 6. TAE 记账相关设计摘要
                ## 7. 数据模型摘要
                ## 8. 状态机摘要
                ## 9. 幂等、事务、补偿、冲正、对账摘要
                ## 10. 外部系统调用关系
                ## 11. 差异化扩展点清单
                ## 12. 代码组件映射
                ## 13. 待确认问题
                
                ===== 合并摘要 =====
                %s
                """.formatted(finalSummary);

        finalPrompt = promptBudget.limit(finalPrompt, promptBudget.maxPromptChars());

        try {
            return llmClient.chat(PromptBuilder.systemPrompt(), finalPrompt);
        } catch (Exception e) {
            return """
                    # 系统基线知识摘要
                    
                    最终摘要生成失败，返回最后一轮合并摘要。
                    
                    失败原因：
                    %s
                    
                    ===== 最后一轮合并摘要 =====
                    %s
                    """.formatted(e.getMessage(), finalSummary);
        }
    }

    private List<List<String>> groupByCharBudget(List<String> texts, int maxChars) {
        List<List<String>> groups = new ArrayList<>();

        List<String> current = new ArrayList<>();
        int currentLen = 0;

        for (String text : texts) {
            String safeText = text == null ? "" : text;
            int len = safeText.length();

            if (!current.isEmpty() && currentLen + len > maxChars) {
                groups.add(current);
                current = new ArrayList<>();
                currentLen = 0;
            }

            if (len > maxChars) {
                current.add(safeText.substring(0, maxChars) + "\n\n...摘要过长，已截断...");
                groups.add(current);
                current = new ArrayList<>();
                currentLen = 0;
            } else {
                current.add(safeText);
                currentLen += len;
            }
        }

        if (!current.isEmpty()) {
            groups.add(current);
        }

        return groups;
    }

    /**
     * 生成某个文档时，不把所有 chunk summary 都塞进去。
     * 只使用 baseline + 关键词相关摘要。
     */
    private String buildDocKnowledge(String path,
                                     String goal,
                                     String baselineSummary,
                                     List<String> chunkSummaries) {
        int maxChars = properties.getGeneration().getDocKnowledgeChars();

        StringBuilder builder = new StringBuilder();

        builder.append("# 基线摘要\n\n")
                .append(promptBudget.limit(baselineSummary, maxChars / 2))
                .append("\n\n");

        List<String> selected = selectRelevantSummaries(path, goal, chunkSummaries, 5);

        builder.append("# 相关源码切片摘要\n\n");

        for (String summary : selected) {
            if (builder.length() + summary.length() > maxChars) {
                break;
            }

            builder.append(summary).append("\n\n");
        }

        return promptBudget.limit(builder.toString(), maxChars);
    }

    private List<String> selectRelevantSummaries(String path,
                                                 String goal,
                                                 List<String> summaries,
                                                 int limit) {
        List<String> keywords = buildKeywords(path, goal);

        return summaries.stream()
                .sorted((a, b) -> Integer.compare(score(b, keywords), score(a, keywords)))
                .limit(limit)
                .toList();
    }

    private List<String> buildKeywords(String path, String goal) {
        String text = (path + " " + goal).toLowerCase();

        List<String> keywords = new ArrayList<>();

        if (text.contains("tae") || text.contains("记账") || text.contains("accounting")) {
            keywords.addAll(List.of(
                    "tae",
                    "accounting",
                    "记账",
                    "回调",
                    "流水",
                    "冲正",
                    "补偿",
                    "对账"
            ));
        }

        if (text.contains("capabil") || text.contains("业务能力")) {
            keywords.addAll(List.of(
                    "能力",
                    "交易",
                    "场景",
                    "放款",
                    "还款",
                    "入账",
                    "出账",
                    "签约"
            ));
        }

        if (text.contains("data") || text.contains("model") || text.contains("数据")) {
            keywords.addAll(List.of(
                    "table",
                    "mapper",
                    "entity",
                    "表",
                    "字段",
                    "状态",
                    "流水号"
            ));
        }

        if (text.contains("entry") || text.contains("入口")) {
            keywords.addAll(List.of(
                    "controller",
                    "callback",
                    "listener",
                    "job",
                    "接口",
                    "入口"
            ));
        }

        if (text.contains("extension") || text.contains("扩展")) {
            keywords.addAll(List.of(
                    "扩展点",
                    "校验",
                    "规则",
                    "策略",
                    "差异",
                    "改造"
            ));
        }

        keywords.addAll(List.of(
                "loan",
                "deposit",
                "contract",
                "common",
                "贷款",
                "存款",
                "合约",
                "公共"
        ));

        return keywords;
    }

    private int score(String text, List<String> keywords) {
        if (text == null) {
            return 0;
        }

        String lower = text.toLowerCase();
        int score = 0;

        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                score++;
            }
        }

        return score;
    }

    private void writeFile(Path outputDir, String relativePath, String content) throws IOException {
        Path file = outputDir.resolve(relativePath).normalize();

        if (!file.startsWith(outputDir)) {
            throw new IOException("非法输出路径: " + relativePath);
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, content == null ? "" : content);
    }

    private Map<String, String> buildDocTasks() {
        Map<String, String> tasks = new LinkedHashMap<>();

        tasks.put("README.md", """
                生成当前文档包的说明文档。
                说明文档用途、目录结构、如何用于银行差异需求详细设计、如何给 coding 工具使用。
                """);

        tasks.put("docs/00-system-overview/system-overview.md", """
                生成系统总览文档。
                说明系统定位、技术架构、分布式调用方式、核心模块、工程结构、关键交易链路。
                """);

        tasks.put("docs/00-system-overview/system-boundary.md", """
                生成系统边界文档。
                区分贷款、存款、公共、合约中心、TAE 等系统职责。
                如果源码中只能看到部分系统，需要标注待确认。
                """);

        tasks.put("docs/01-capabilities/capability-list.md", """
                生成业务能力清单。
                按业务能力/交易场景整理，例如贷款放款、贷款还款、存款入账、TAE回调、冲正、补偿、对账。
                每个能力需要关联入口、主服务、TAE、表、状态、扩展点。
                """);

        tasks.put("docs/02-entrypoints/entrypoints.md", """
                生成入口层清单。
                包括 Controller、内部接口、TAE 回调、MQ Listener、Job、批处理入口。
                每个入口说明对应业务能力、路径、类、方法、幂等字段。
                """);

        tasks.put("docs/03-components/components.md", """
                生成代码组件映射文档。
                按 Controller、Application Service、Rule Service、Client、Assembler、Mapper、Job、Callback 等分类。
                每个组件说明职责和对应业务能力。
                """);

        tasks.put("docs/04-tae/tae-accounting.md", """
                生成 TAE 记账引擎交互设计文档。
                说明 TAE 调用点、场景码、请求组装、回调接口、TAE 流水号、幂等、超时、补偿、冲正、对账。
                """);

        tasks.put("docs/05-data-model/data-model.md", """
                生成数据模型文档。
                整理表、实体、Mapper、关键字段、状态字段、业务流水号、TAE 流水号、幂等字段、唯一索引建议。
                """);

        tasks.put("docs/06-extension-points/extension-points.md", """
                生成差异化扩展点文档。
                列出前置校验扩展、业务规则扩展、TAE参数扩展、回调处理扩展、状态机扩展、补偿/冲正扩展。
                明确每个扩展点对应类、方法、适用差异需求。
                """);

        tasks.put("docs/07-change-design-template/diff-requirement-design-template.md", """
                生成银行差异需求详细设计模板。
                模板需要包含：需求背景、影响业务能力、当前基线、影响范围、接口改造、数据库改造、TAE改造、状态机、幂等事务补偿、代码改造点、测试用例、上线回滚。
                """);

        tasks.put("metadata/capabilities.yaml", """
                生成机器可读的业务能力元数据。
                必须输出合法 YAML。
                字段建议包含 capabilityId、capabilityName、system、entrypoints、coreServices、tae、tables、states、extensionPoints、uncertainItems。
                """);

        return tasks;
    }
}