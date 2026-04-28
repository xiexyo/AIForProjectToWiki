package com.bank.docgen.prompt;

public class PromptBuilder {

    public static String systemPrompt() {
        return """
                你是资深银行核心系统架构师、Java/Spring 分布式系统专家、详细设计文档专家。
                
                你正在分析一个银行核心后端系统源码。
                
                系统可能包含：
                - 存款系统
                - 贷款系统
                - 公共系统
                - 合约中心
                - TAE 记账引擎调用
                - TAE 回调处理
                - 分布式事务
                - 幂等
                - 补偿
                - 冲正
                - 对账
                - 定时任务
                - MQ 消费
                
                你的任务不是泛泛介绍代码，而是生成“当前系统基线设计文档”，用于后续银行差异需求生成详细设计和指导 coding 工具改代码。
                
                严格要求：
                1. 只能基于源码中能看出来的信息进行分析。
                2. 不确定的信息必须标注为“待确认”，不要编造。
                3. 重要结论必须尽量带上代码路径、类名、方法名、表名、接口路径。
                4. 资金类流程必须关注 TAE、状态机、幂等、事务、补偿、冲正、对账。
                5. 输出必须结构化，方便人阅读，也方便工具二次处理。
                """;
    }

    public static String chunkAnalysisPrompt(String fileTree,
                                             String chunkContent,
                                             int maxSummaryChars) {
        return """
                请分析下面这一批源码文件，提取银行核心系统基线设计信息。
                
                重要限制：
                1. 本次输出不要超过 %s 字。
                2. 不要复述源码。
                3. 只提取对详细设计和差异化开发有价值的信息。
                4. 不确定的信息标注“待确认”。
                5. 每个结论尽量带代码路径、类名、方法名、表名。
                
                重点识别：
                - 业务能力/交易场景
                - Controller / 回调 / MQ / Job 入口
                - 主流程 Service
                - 规则类、校验类、策略类
                - TAE 调用、TAE 回调、场景码、流水号
                - Entity、Mapper、表、状态字段、幂等字段
                - 状态机、补偿、冲正、对账
                - 差异化扩展点
                
                输出格式：
                
                # 源码片段分析摘要
                
                ## 1. 业务能力
                
                | 能力编号建议 | 能力名称 | 证据文件 | 说明 |
                |---|---|---|---|
                
                ## 2. 入口
                
                | 类型 | 路径/Topic/Job | 类 | 方法 | 对应能力 |
                |---|---|---|---|---|
                
                ## 3. 核心组件
                
                | 组件类型 | 类/文件 | 方法 | 职责 | 对应能力 |
                |---|---|---|---|---|
                
                ## 4. TAE 相关
                
                | 类型 | 文件 | 方法/字段 | 说明 |
                |---|---|---|---|
                
                ## 5. 数据模型
                
                | 表/实体/Mapper | 文件 | 关键字段 | 说明 |
                |---|---|---|---|
                
                ## 6. 状态、幂等、补偿、冲正、对账
                
                | 类型 | 文件 | 方法/字段 | 说明 |
                |---|---|---|---|
                
                ## 7. 扩展点
                
                | 扩展点编号建议 | 扩展点名称 | 文件 | 方法 | 适用差异需求 |
                |---|---|---|---|---|
                
                ## 8. 待确认
                
                - ...
                
                ===== 仓库文件树节选 =====
                %s
                
                ===== 本批源码 =====
                %s
                """.formatted(
                maxSummaryChars,
                limit(fileTree, 12000),
                chunkContent
        );
    }

    public static String docPrompt(String path,
                                   String docGoal,
                                   String fileTree,
                                   String knowledge) {
        return """
                请根据“系统基线知识”和“仓库文件树”，生成指定文档。
                
                当前要生成的文件路径：
                %s
                
                文档目标：
                %s
                
                通用要求：
                1. 输出纯文档内容，不要使用 ``` 包裹全文。
                2. 如果是 Markdown，使用清晰标题、表格、列表。
                3. 如果是 YAML，必须输出合法 YAML。
                4. 以“业务能力/交易场景”为主线，而不是单纯按接口或代码包描述。
                5. 对资金类、TAE 相关流程要体现：状态机、幂等、事务、补偿、冲正、对账。
                6. 对后续差异需求开发有价值的地方，要明确“改哪里”“涉及哪些类/表/接口”。
                7. 不确定的信息写“待确认”，不要编造。
                
                ===== 仓库文件树 =====
                %s
                
                ===== 系统基线知识 =====
                %s
                """.formatted(
                path,
                docGoal,
                limit(fileTree, 12000),
                knowledge
        );
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n\n...内容过长已截断...";
    }
}