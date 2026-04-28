# 源码片段分析摘要

## 1. 业务能力

| 能力编号建议 | 能力名称 | 证据文件 | 说明 |
|---|---|---|---|
| CAP-001 | 客户信息管理 | `CustomerController.java`, `CustomerService.java`, `CustomerServiceImpl.java` | 创建零售客户，维护客户主数据。 |
| CAP-002 | 账户信息管理 | `AccountController.java`, `AccountService.java`, `AccountServiceImpl.java` | 为客户开立账户，查询账户详情。 |
| CAP-003 | 产品信息查询 | `ProductRepository.java`, `ProductRepositoryImpl.java` | 查询产品配置信息，用于开户等场景。 |
| CAP-004 | 交易流水记录 | `TransactionEntity.java`, `TransactionMapper.java` | 记录资金交易流水，为后续交易类功能预留。 |
| CAP-005 | 幂等控制 | `IdempotentRecordEntity.java`, `IdempotentRecordMapper.java` | 防止重复请求，为后续交易、开户等场景预留。 |
| CAP-006 | 系统健康检查 | `HealthController.java` | 提供应用健康状态检查接口。 |

## 2. 入口

| 类型 | 路径/Topic/Job | 类 | 方法 | 对应能力 |
|---|---|---|---|---|
| HTTP REST | `POST /api/v1/customers` | `CustomerController` | `createCustomer` | CAP-001 |
| HTTP REST | `POST /api/v1/accounts/open` | `AccountController` | `openAccount` | CAP-002 |
| HTTP REST | `GET /api/v1/accounts/{accountNo}` | `AccountController` | `getAccountDetail` | CAP-002 |
| HTTP REST | `GET /health` | `HealthController` | `health` | CAP-006 |
| 全局异常处理 | N/A | `GlobalExceptionHandler` | `handleBizException`, `handleValidationException`, `handleException` | 所有能力 |

## 3. 核心组件

| 组件类型 | 类/文件 | 方法 | 职责 | 对应能力 |
|---|---|---|---|---|
| 业务服务 | `CustomerServiceImpl` | `createCustomer` | 客户创建主逻辑，包含唯一性校验、实体组装、持久化。 | CAP-001 |
| 业务服务 | `AccountServiceImpl` | `openAccount` | 账户开立主逻辑，包含客户/产品校验、一类户规则校验、实体组装、持久化。 | CAP-002 |
| 业务服务 | `AccountServiceImpl` | `getAccountDetail` | 聚合账户、客户、产品信息，组装详情响应。 | CAP-002 |
| 数据仓储 | `CustomerRepositoryImpl` | `save`, `findByCustomerId`, `findByCertNo`, `existsByCertNo` | 封装客户数据访问，调用 `CustomerMapper`。 | CAP-001 |
| 数据仓储 | `AccountRepositoryImpl` | `save`, `findByAccountNo`, `existsActiveClassOneAccount` | 封装账户数据访问，调用 `AccountMapper`。 | CAP-002 |
| 数据仓储 | `ProductRepositoryImpl` | `findByProductCode` | 封装产品数据访问，调用 `ProductMapper`。 | CAP-003 |
| 数据访问 | `CustomerMapper` | `insert`, `selectByCustomerId`, `selectByCertNo`, `countByCertNo` | 直接操作 `t_customer` 表。 | CAP-001 |
| 数据访问 | `AccountMapper` | `insert`, `selectByAccountNo`, `countActiveClassOneAccountByCustomerId` | 直接操作 `t_account` 表。 | CAP-002 |
| 数据访问 | `ProductMapper` | `selectByProductCode` | 直接操作 `t_product` 表。 | CAP-003 |
| 规则校验 | `AccountServiceImpl` | `openAccount` 方法内嵌 | 校验客户存在、产品存在且可售、一类户唯一性。 | CAP-002 |
| 规则校验 | `CustomerServiceImpl` | `createCustomer` 方法内嵌 | 校验证件号唯一性。 | CAP-001 |
| 统一响应 | `ApiResponse` | `success`, `fail` | 定义统一的 API 响应结构。 | 所有能力 |
| 业务异常 | `BizException` | 构造函数 | 承载可预期的业务失败场景。 | 所有能力 |

## 4. TAE 相关

| 类型 | 文件 | 方法/字段 | 说明 |
|---|---|---|---|
| 待确认 | 无 | 无 | **当前源码中未发现任何与 TAE (记账引擎) 相关的调用、回调、场景码或流水号字段。** 这是一个 POC 演示工程，资金记账核心逻辑尚未实现。 |

## 5. 数据模型

| 表/实体/Mapper | 文件 | 关键字段 | 说明 |
|---|---|---|---|
| `t_customer` / `CustomerEntity` / `CustomerMapper` | `CustomerEntity.java`, `CustomerMapper.java`, `CustomerMapper.xml` | `customer_id` (PK), `cert_no` (唯一), `customer_status`, `risk_level` | 客户主表。`cert_no` 用于唯一性校验。状态和风险等级为字符串。 |
| `t_product` / `ProductEntity` / `ProductMapper` | `ProductEntity.java`, `ProductMapper.java`, `ProductMapper.xml` | `product_code` (PK), `sale_status`, `account_level` | 产品配置表。`sale_status` 控制是否可售，`account_level` (如 `CLASS_I`) 用于一类户规则。 |
| `t_account` / `AccountEntity` / `AccountMapper` | `AccountEntity.java`, `AccountMapper.java`, `AccountMapper.xml` | `account_no` (PK), `customer_id`, `product_code`, `balance`, `account_status`, `account_type` | 账户主表。关联客户和产品。余额为 `BigDecimal`。状态为字符串。`AccountMapper.countActiveClassOneAccountByCustomerId` 实现一类户规则。 |
| `t_transaction` / `TransactionEntity` / `TransactionMapper` | `TransactionEntity.java`, `TransactionMapper.java`, `TransactionMapper.xml` | `txn_id` (PK), `request_id`, `debit_account_no`, `credit_account_no`, `txn_type`, `txn_status`, `amount` | 交易流水表。预留了借贷方账号、交易类型、状态、金额等字段，支持后续资金交易。`request_id` 可用于幂等。 |
| `t_idempotent_record` / `IdempotentRecordEntity` / `IdempotentRecordMapper` | `IdempotentRecordEntity.java`, `IdempotentRecordMapper.java`, `IdempotentRecordMapper.xml` | `request_id`, `business_type`, `business_key`, `process_status`, `response_code`, `response_message` | 幂等记录表。通过 `request_id` + `business_type` 标识唯一请求。记录处理状态和结果。 |

## 6. 状态、幂等、补偿、冲正、对账

| 类型 | 文件 | 方法/字段 | 说明 |
|---|---|---|---|
| **状态** | `CustomerEntity.java` | `customer_status` | 客户状态，例如 `ACTIVE`。当前逻辑中创建后默认为 `ACTIVE`。 |
| **状态** | `AccountEntity.java` | `account_status` | 账户状态，例如 `ACTIVE`。开户后默认为 `ACTIVE`。 |
| **状态** | `ProductEntity.java` | `sale_status` | 产品销售状态，例如 `ON_SALE`。开户时校验必须为 `ON_SALE`。 |
| **状态** | `TransactionEntity.java` | `txn_status` | 交易状态，例如 `INIT`, `SUCCESS`, `FAIL`。**当前未使用**。 |
| **状态** | `IdempotentRecordEntity.java` | `process_status` | 幂等记录处理状态，例如 `PROCESSING`, `SUCCESS`, `FAIL`。**当前未使用**。 |
| **幂等** | `IdempotentRecordEntity.java`, `IdempotentRecordMapper.java` | `request_id`, `business_type`, `business_key` | 幂等表结构和 Mapper 已预留，**但当前业务服务 (`CustomerServiceImpl`, `AccountServiceImpl`) 中未实现幂等控制逻辑**。 |
| **幂等** | `TransactionEntity.java` | `request_id` | 交易流水表预留了幂等字段，**当前未使用**。 |
| **补偿/冲正** | 无 | 无 | **当前源码中未发现任何补偿 (Compensation) 或冲正 (Reversal) 相关的设计、表字段或逻辑。** |
| **对账** | 无 | 无 | **当前源码中未发现任何对账 (Reconciliation) 相关的设计、Job 或逻辑。** |
| **事务** | 待确认 | 待确认 | 服务层方法未显式使用 `@Transactional` 注解。事务边界依赖于 MyBatis 的默认行为或 Spring 的声明式事务配置（配置文件中未提供）。**待确认**。 |

## 7. 扩展点

| 扩展点编号建议 | 扩展点名称 | 文件 | 方法 | 适用差异需求 |
|---|---|---|---|---|
| EXT-001 | **客户创建规则扩展** | `CustomerServiceImpl` | `createCustomer` | 1. 增加更复杂的客户信息校验（如黑名单）。2. 支持不同渠道的客户创建流程差异。3. 客户创建后触发其他业务（如发送短信）。 |
| EXT-002 | **账户开立规则扩展** | `AccountServiceImpl` | `openAccount` | 1. 增加开户金额门槛。2. 根据客户风险等级 (`risk_level`) 限制可开产品。3. 支持联名开户。4. 开户后初始化积分、权益等。 |
| EXT-003 | **产品信息获取扩展** | `ProductRepositoryImpl` | `findByProductCode` | 1. 产品信息缓存。2. 根据客户属性返回差异化产品。3. 从外部系统获取实时产品信息。 |
| EXT-004 | **账户详情聚合扩展** | `AccountServiceImpl` | `getAccountDetail` | 1. 在详情中增加更多关联信息，如最近交易、持有理财产品、信用额度等。 |
| EXT-005 | **幂等控制框架接入** | `IdempotentRecordMapper` | `insert`, `selectByRequestId` | 1. 为所有写接口（开户、存款、转账）增加幂等控制。2. 设计统一的幂等切面 (AOP)。 |
| EXT-006 | **交易流水记录扩展** | `TransactionMapper` | `insert` | 1. 实现存款、取款、转账等资金交易，并调用此 Mapper 记录流水。2. 增加更多流水字段（如手续费、渠道）。 |
| EXT-007 | **状态机引擎引入** | 无 (需新建) | 无 | 1. 管理客户、账户、交易状态的复杂变迁。2. 替代当前简单的字符串状态字段。 |
| EXT-008 | **TAE 记账服务接入** | 无 (需新建) | 无 | 1. 实现资金类交易的核心记账。2. 定义场景码、调用 TAE 的 Service、处理 TAE 回调。3. 与 `t_transaction` 流水关联。 |

## 8. 待确认

- **事务管理**：`application.yml` 未提供，无法确认 Spring 事务是如何配置的。服务层方法是否在事务中运行待确认。
- **主键生成策略**：`CustomerEntity.customerId` 和 `AccountEntity.accountNo` 的生成规则在代码中未明确体现，仅在 `ReadMe.md` 中提及为“时间戳+随机数”，具体实现位置待确认。
- **分布式架构**：当前为单体应用，未发现微服务、分布式事务（Seata等）、MQ消息、定时任务 Job 的相关代码。
- **TAE 集成**：如前所述，完全缺失。任何涉及资金余额变动的需求（存款、转账）都需要全新设计 TAE 集成层。
- **补偿与冲正**：完全缺失。资金交易异常后的回滚机制需要全新设计。
- **对账**：完全缺失。与会计系统或渠道的对账功能需要全新设计。
- **枚举使用**：当前所有状态字段（如 `customer_status`, `account_status`）均使用 `String` 类型，未使用 Java 枚举，类型安全性较弱。