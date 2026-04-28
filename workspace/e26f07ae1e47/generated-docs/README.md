# 银行核心系统 POC 项目说明文档

## 1. 文档用途

本文档旨在为银行核心系统 POC（概念验证）项目提供基线说明。它描述了当前系统的架构、已实现的核心业务能力、数据模型、关键设计模式以及为后续完整银行核心系统开发预留的扩展点。

**核心用途：**
1.  **理解当前基线**：帮助新加入的架构师、开发人员快速了解系统现状，明确“已有什么”和“缺什么”。
2.  **指导差异需求详细设计**：当银行提出新的业务需求（如新增交易类型、修改开户规则）时，本文档的“差异化扩展点清单”和“代码组件映射”可直接指导设计人员定位需要修改的代码、表结构和流程。
3.  **为 Coding 工具提供上下文**：自动化代码生成或重构工具可以基于本文档的结构化信息（如业务能力清单、数据模型、扩展点），精确地定位代码插入点、生成样板代码或执行特定重构。

## 2. 项目目录结构说明

```
poc/
├── pom.xml                              # Maven 项目配置文件
├── ReadMe.md                            # 项目原始说明（待替换为本文档）
├── src/
│   ├── main/
│   │   ├── java/com/bank/poc/
│   │   │   ├── RetailBankPocApplication.java # Spring Boot 主启动类
│   │   │   ├── common/                    # 通用组件包
│   │   │   │   ├── api/
│   │   │   │   │   └── ApiResponse.java   # 统一API响应体
│   │   │   │   └── exception/
│   │   │   │       └── BizException.java  # 业务异常类
│   │   │   ├── config/
│   │   │   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   │   │   ├── controller/               # HTTP 入口层
│   │   │   │   ├── AccountController.java
│   │   │   │   ├── CustomerController.java
│   │   │   │   └── HealthController.java
│   │   │   ├── dto/                      # 数据传输对象（请求/响应）
│   │   │   │   ├── request/
│   │   │   │   │   ├── CreateCustomerRequest.java
│   │   │   │   │   └── OpenAccountRequest.java
│   │   │   │   └── response/
│   │   │   │       ├── AccountDetailResponse.java
│   │   │   │       ├── CreateCustomerResponse.java
│   │   │   │       └── OpenAccountResponse.java
│   │   │   ├── entity/                   # 数据库实体类（对应表）
│   │   │   │   ├── AccountEntity.java
│   │   │   │   ├── CustomerEntity.java
│   │   │   │   ├── IdempotentRecordEntity.java
│   │   │   │   ├── ProductEntity.java
│   │   │   │   └── TransactionEntity.java
│   │   │   ├── mapper/                   # MyBatis Mapper 接口
│   │   │   │   ├── AccountMapper.java
│   │   │   │   ├── CustomerMapper.java
│   │   │   │   ├── IdempotentRecordMapper.java
│   │   │   │   ├── ProductMapper.java
│   │   │   │   └── TransactionMapper.java
│   │   │   ├── repository/               # 仓储层接口与实现
│   │   │   │   ├── AccountRepository.java
│   │   │   │   ├── CustomerRepository.java
│   │   │   │   ├── ProductRepository.java
│   │   │   │   └── impl/
│   │   │   │       ├── AccountRepositoryImpl.java
│   │   │   │       ├── CustomerRepositoryImpl.java
│   │   │   │       └── ProductRepositoryImpl.java
│   │   │   └── service/                  # 业务服务层
│   │   │       ├── AccountService.java
│   │   │       ├── CustomerService.java
│   │   │       └── impl/
│   │   │           ├── AccountServiceImpl.java
│   │   │           └── CustomerServiceImpl.java
│   │   └── resources/
│   │       ├── application.yml           # Spring Boot 配置文件
│   │       ├── data.sql                  # 初始化数据脚本
│   │       ├── schema.sql                # 数据库建表脚本
│   │       └── mapper/                   # MyBatis SQL 映射文件
│   │           ├── AccountMapper.xml
│   │           ├── CustomerMapper.xml
│   │           ├── IdempotentRecordMapper.xml
│   │           ├── ProductMapper.xml
│   │           └── TransactionMapper.xml
│   └── test/                             # 单元测试
│       └── java/com/bank/poc/service/impl/
│           └── AccountServiceImplTest.java
```

## 3. 如何用于银行差异需求详细设计

当接到一个新的银行差异需求时，可按以下步骤使用本文档进行详细设计：

1.  **需求分析与能力映射**：
    *   分析需求属于哪个核心业务能力（参考“业务能力清单”）。
    *   例如，需求“为VIP客户开立专属理财账户”可能涉及 `CAP-002 (账户开立)`，并需要扩展 `CAP-003 (账户信息查询)` 以展示专属信息。

2.  **定位入口与流程**：
    *   确定需求是同步HTTP请求、异步MQ消息还是定时任务。
    *   参考“入口层清单”，决定是复用现有入口（如 `/api/v1/accounts/open`）还是创建新入口。
    *   若创建新入口，需在 `controller` 包下新建类，并遵循现有的 `ApiResponse` 统一响应格式。

3.  **识别修改与扩展点**：
    *   对照“差异化扩展点清单”，找到最相关的扩展点。
    *   例如，上述VIP开户需求可能触发：
        *   **EXT-003 (账户开立规则扩展)**：在 `AccountServiceImpl.openAccount` 方法中增加VIP客户校验和专属产品匹配逻辑。
        *   **EXT-006 (产品可售性校验扩展)**：修改 `ProductRepositoryImpl.findByProductCode` 或相关校验逻辑，加入客户等级维度。
        *   **EXT-005 (账户编号生成策略)**：可能需要为VIP账户生成特殊前缀的账号，修改 `AccountServiceImpl.generateAccountNo` 方法。

4.  **设计资金与TAE流程（如涉及）**：
    *   **关键**：如果需求涉及资金变动（存款、转账、缴费），**当前基线完全缺失此部分**。
    *   必须全新设计，参考“TAE记账相关设计摘要”和“幂等、事务、补偿、冲正、对账摘要”。
    *   设计步骤应包括：
        a. 定义TAE场景码和DTO。
        b. 在 `service` 包下创建新的交易服务（如 `TransferService`）。
        c. 服务方法需集成幂等控制（利用 `t_idempotent_record` 表）、记录业务流水（`t_transaction` 表）、调用TAE记账引擎。
        d. 设计TAE回调处理器（新的 `Controller` 或 `@Component`），用于接收记账结果并更新业务流水状态。
        e. 考虑补偿/冲正逻辑，在 `txn_status` 中增加相应状态。

5.  **数据模型变更**：
    *   检查“数据模型摘要”，确定是否需要新增表或为现有表（如 `t_account`, `t_product`）增加字段。
    *   例如，VIP专属账户可能需要增加 `account_attribute` 或 `vip_level` 字段。
    *   修改对应的 `Entity` 类、`Mapper` 接口和 XML 文件。

6.  **状态机设计**：
    *   如果需求引入新的复杂状态流转（如贷款申请流程），当前基线无状态机引擎。
    *   需要设计状态枚举，并评估引入状态机框架（如Spring StateMachine）的必要性，参考“状态机摘要”。

7.  **产出详细设计文档**：
    *   基于以上分析，产出包含“修改的类/方法/SQL”、“新增的接口/DTO”、“流程时序图”、“状态流转图”的详细设计文档。

## 4. 如何给 Coding 工具使用

本文档的结构化信息可用于驱动自动化编码辅助工具：

1.  **组件代码生成**：
    *   **输入**：工具读取“代码组件映射”和“业务能力清单”。
    *   **动作**：当需求要求新增一个“贷款发放”能力时，工具可自动生成标准化的组件骨架：
        *   `LoanController.java` (基于 `AccountController` 模板)
        *   `LoanService.java` 和 `LoanServiceImpl.java` (基于 `AccountServiceImpl` 模板)
        *   `LoanEntity.java` 和 `t_loan` 建表语句 (基于现有Entity模式)
        *   对应的 `LoanMapper.java`, `LoanMapper.xml`, `LoanRepository.java`, `LoanRepositoryImpl.java`
    *   **关键路径**：工具需知道代码模板位置和包结构约定。

2.  **TAE集成代码生成**：
    *   **输入**：工具识别到需求涉及“资金记账”。
    *   **动作**：工具可生成TAE集成的基础代码包，包括：
        *   `tae/client/TaeAccountingClient.java` (模拟)
        *   `tae/dto/TaeRequest.java`, `TaeResponse.java`
        *   `tae/callback/TaeCallbackController.java`
        *   在交易服务模板中自动插入幂等检查、流水记录、TAE调用的代码块。

3.  **规则校验插入**：
    *   **输入**：工具读取“规则层/策略层清单”和“扩展点”。
    *   **动作**：当需求要求在开户时增加“年龄必须满18岁”的规则，工具可定位到 `AccountServiceImpl.openAccount` 方法，在现有的客户存在性校验后，自动插入一段规则校验代码，并抛出 `BizException`。

4.  **依赖与配置更新**：
    *   **输入**：工具分析新增的组件和外部依赖（如Redis、MQ）。
    *   **动作**：自动更新 `pom.xml` 中的依赖，或在 `application.yml` 中提示需要添加的新配置项。

**使用前提**：Coding工具需要能解析本Markdown文档的结构，或将其转换为机器可读的格式（如JSON Schema）。文档中明确的类名、方法名、文件路径为工具提供了精准的定位坐标。

---
**文档状态**：基线版本
**对应代码版本**：POC 初始版本
**备注**：本系统为演示框架，生产级资金交易、TAE集成、分布式事务等核心能力需基于此基线进行大规模扩展和重构。