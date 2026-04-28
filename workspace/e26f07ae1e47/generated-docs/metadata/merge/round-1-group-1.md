# 合并摘要

## 1. 业务能力
基于源码分析，当前系统是一个银行核心系统的POC或演示版本，主要实现了客户、账户、产品的基础管理能力，并预留了交易流水和幂等控制的结构。**资金类交易（存款、转账等）的核心记账功能（TAE集成）尚未实现。**

| 能力编号 | 能力名称 | 核心证据文件 | 说明 |
|---|---|---|---|
| CAP-001 | 客户信息管理 | `CustomerController.java`, `CustomerServiceImpl.java`, `CustomerMapper.xml` | 创建零售客户，校验证件号唯一性，生成客户号并持久化。 |
| CAP-002 | 账户开立 | `AccountController.java`, `AccountServiceImpl.java`, `AccountMapper.xml` | 为客户开立账户，校验客户存在、产品可售、一类户唯一性规则，生成账号并持久化。 |
| CAP-003 | 账户信息查询 | `AccountController.java`, `AccountServiceImpl.java` | 聚合查询账户详情，包含关联的客户和产品信息。 |
| CAP-004 | 产品信息查询 | `ProductRepositoryImpl.java`, `ProductMapper.xml` | 根据产品代码查询产品配置信息，用于开户等场景的规则校验。 |
| CAP-005 | 交易流水记录（结构预留） | `TransactionEntity.java`, `TransactionMapper.xml` | 定义了交易流水表`t_transaction`的结构和Mapper，**当前未在业务逻辑中使用**，为后续资金交易预留。 |
| CAP-006 | 幂等控制（结构预留） | `IdempotentRecordEntity.java`, `IdempotentRecordMapper.xml` | 定义了幂等记录表`t_idempotent_record`的结构和Mapper，**当前未在业务逻辑中使用**，为后续防重请求预留。 |
| CAP-007 | 系统健康检查 | `HealthController.java` | 提供应用健康状态检查接口。 |

## 2. 入口
系统当前为基于Spring Boot的HTTP REST服务，未发现MQ消费者、定时任务等异步入口。

| 类型 | 路径 | 类 | 方法 | 对应能力 |
|---|---|---|---|---|
| HTTP REST | `POST /api/v1/customers` | `CustomerController` | `createCustomer` | CAP-001 |
| HTTP REST | `POST /api/v1/accounts/open` | `AccountController` | `openAccount` | CAP-002 |
| HTTP REST | `GET /api/v1/accounts/{accountNo}` | `AccountController` | `getAccountDetail` | CAP-003 |
| HTTP REST | `GET /health` | `HealthController` | `health` | CAP-007 |
| 全局异常处理 | N/A | `GlobalExceptionHandler` | `handleBizException`等 | 统一处理所有HTTP入口的异常。 |

## 3. 主流程组件
核心业务逻辑集中在Service层，采用Repository模式封装数据访问。

| 组件类型 | 类/文件 | 关键方法 | 职责与流程 | 对应能力 |
|---|---|---|---|---|
| **业务服务** | `CustomerServiceImpl` | `createCustomer` | 1. 调用`CustomerRepository.existsByCertNo`校验证件号唯一性。<br>2. 调用私有方法`generateCustomerId`生成客户号（规则：`C`+时间+4位随机数）。<br>3. 组装`CustomerEntity`并设置默认状态(`ACTIVE`)和风险等级(`R2`)。<br>4. 调用`CustomerRepository.save`持久化。 | CAP-001 |
| **业务服务** | `AccountServiceImpl` | `openAccount` | 1. 调用`CustomerRepository.findByCustomerId`校验客户存在。<br>2. 调用`ProductRepository.findByProductCode`校验产品存在且`sale_status`为`ON_SALE`。<br>3. 调用`AccountRepository.existsActiveClassOneAccount`校验一类户唯一性规则（通过`AccountMapper.countActiveClassOneAccountByCustomerId`实现）。<br>4. 调用私有方法`generateAccountNo`生成账号（规则：`62`+时间+6位随机数）。<br>5. 组装`AccountEntity`并设置默认状态(`ACTIVE`)。<br>6. 调用`AccountRepository.save`持久化。 | CAP-002 |
| **业务服务** | `AccountServiceImpl` | `getAccountDetail` | 1. 调用`AccountRepository.findByAccountNo`获取账户实体。<br>2. 调用`CustomerRepository.findByCustomerId`获取客户实体。<br>3. 调用`ProductRepository.findByProductCode`获取产品实体。<br>4. 聚合三者信息返回。 | CAP-003 |
| **仓储层** | `CustomerRepositoryImpl` | `save`, `findByCustomerId`, `existsByCertNo` | 封装对`t_customer`表的访问，委托给`CustomerMapper`。 | CAP-001 |
| **仓储层** | `AccountRepositoryImpl` | `save`, `findByAccountNo`, `existsActiveClassOneAccount` | 封装对`t_account`表的访问，委托给`AccountMapper`。 | CAP-002 |
| **仓储层** | `ProductRepositoryImpl` | `findByProductCode` | 封装对`t_product`表的访问，委托给`ProductMapper`。 | CAP-004 |
| **数据访问层** | `CustomerMapper.xml` | `insert`, `selectByCustomerId`, `countByCertNo` | 直接操作`t_customer`表的SQL映射。 | CAP-001 |
| **数据访问层** | `AccountMapper.xml` | `insert`, `selectByAccountNo`, `countActiveClassOneAccountByCustomerId` | 直接操作`t_account`表的SQL映射。其中`countActiveClassOneAccountByCustomerId`实现一类户规则。 | CAP-002 |
| **数据访问层** | `ProductMapper.xml` | `selectByProductCode` | 直接操作`t_product`表的SQL映射。 | CAP-004 |
| **统一构造** | `ApiResponse` | `success`, `fail` | 定义所有HTTP接口的统一响应格式。 | 通用 |
| **业务异常** | `BizException` | 构造函数 | 用于抛出可预知的业务逻辑异常，由`GlobalExceptionHandler`捕获并转换为`ApiResponse`。 | 通用 |

## 4. TAE 相关
**当前源码中未发现任何与TAE（记账引擎）相关的代码、配置、场景码或回调处理逻辑。**
- 无TAE服务调用接口（如RPC、HTTP Client）。
- 无TAE回调处理器（Controller或Listener）。
- 实体类中无TAE流水号、场景码等字段。
- **结论**：这是一个基础框架演示，**所有涉及资金余额变动（如存款、取款、转账）的交易流程均未实现**。后续开发需全新设计TAE集成层。

## 5. 数据模型
系统包含五张核心表，表结构通过`schema.sql`和MyBatis Mapper定义。

| 实体/表名 | 关键字段（用途） | 关联关系 | 说明 |
|---|---|---|---|
| **`CustomerEntity` (`t_customer`)** | `customer_id` (PK，客户号), `cert_no` (唯一索引，证件号), `customer_status` (状态), `risk_level` (风险等级) | 一对多 `t_account` | 客户主数据。状态和风险等级为`String`类型，无枚举约束。 |
| **`ProductEntity` (`t_product`)** | `product_code` (PK，产品代码), `sale_status` (销售状态), `account_level` (账户等级，如`CLASS_I`), `product_type`, `currency` | 一对多 `t_account` | 产品定义表。`sale_status`控制可售性，`account_level`用于一类户规则。 |
| **`AccountEntity` (`t_account`)** | `account_no` (PK，账号), `customer_id` (FK), `product_code` (FK), `balance` (余额), `account_status` (状态), `open_date` | 多对一 `t_customer`, `t_product` | 账户主表。余额为`BigDecimal`。状态为`String`类型。 |
| **`TransactionEntity` (`t_transaction`)** | `txn_id` (PK), `request_id` (唯一索引), `debit_account_no`, `credit_account_no`, `txn_type`, `txn_status`, `amount`, `txn_time` | 与`t_account`通过账号关联 | **预留结构**。包含借贷方、交易类型、状态、金额、时间等字段，可用于记录业务流水。`request_id`设计用于幂等。 |
| **`IdempotentRecordEntity` (`t_idempotent_record`)** | `request_id` (PK), `business_type`, `business_key`, `process_status`, `response_code`, `response_message` | 无 | **预留结构**。用于实现幂等控制，通过`request_id` + `business_type`标识唯一请求。 |

## 6. 状态机、幂等、补偿、冲正、对账
当前系统在这些资金交易核心领域仅有**基础的结构预留，缺乏完整的逻辑实现**。

| 方面 | 现状分析 | 证据与说明 |
|---|---|---|
| **状态管理** | **简单字段，无状态机**。实体中有状态字段，但仅为`String`类型，无状态枚举定义，无状态转换规则和驱动逻辑。 | `CustomerEntity.customer_status`, `AccountEntity.account_status`, `TransactionEntity.txn_status`, `IdempotentRecordEntity.process_status`。创建后默认设为`ACTIVE`或类似值。 |
| **幂等控制** | **表结构已设计，业务层未集成**。`t_idempotent_record`表和Mapper已就绪，但`CustomerServiceImpl.createCustomer`和`AccountServiceImpl.openAccount`中**未调用**。HTTP接口也未要求传入`request_id`。 | `IdempotentRecordMapper.xml` 包含`insert`和`selectByRequestId`语句。业务服务中无相关代码。 |
| **本地事务** | **部分服务方法使用了声明式事务**。`AccountServiceImpl.openAccount`和`CustomerServiceImpl.createCustomer`方法上使用了`@Transactional`注解。 | 代码中可见`@Transactional`注解，保证了单数据库操作的事务性。 |
| **补偿** | **未实现**。未发现任何补偿交易（Compensation Transaction）相关的设计、表字段或服务方法。 | 无相关代码或表结构。 |
| **冲正** | **未实现**。未发现任何冲正（Reversal）相关的设计、表字段、状态或服务方法。 | 无相关代码或表结构。`txn_status`无“冲正”相关状态。 |
| **对账** | **仅有查询能力预留**。`TransactionMapper.xml`提供了按时间范围查询流水的SQL，但无对账任务、文件生成、差异处理等完整流程。 | `TransactionMapper.xml`中的`selectByTxnTimeBetween`语句。无定时Job或对账服务类。 |
| **分布式事务** | **未涉及**。当前为单体应用，无跨服务调用，因此未使用Seata等分布式事务方案。 | 无相关代码或配置。 |

## 7. 扩展点
以下扩展点基于现有代码结构提炼，可用于指导后续差异化需求的详细设计。

| 扩展点编号 | 扩展点名称 | 关联文件/方法 | 扩展场景示例 |
|---|---|---|---|
| **EXT-001** | **客户创建规则扩展** | `CustomerServiceImpl.createCustomer` | 1. 增加黑名单、反洗钱等复杂校验。<br>2. 根据渠道（如线上、柜面）定制不同流程。<br>3. 客户创建后发送短信、邮件或初始化积分。 |
| **EXT-002** | **客户编号生成策略** | `CustomerServiceImpl.generateCustomerId` (私有方法) | 1. 变更生成规则（如加入机构码、序列号）。<br>2. 从全局序列服务获取ID。 |
| **EXT-003** | **账户开立规则扩展** | `AccountServiceImpl.openAccount` | 1. 增加开户金额门槛、身份验证强度要求。<br>2. 根据客户`risk_level`限制可开产品。<br>3. 支持联名开户、预开户流程。<br>4. 开户后自动签约服务（如短信通知、网银）。 |
| **EXT-004** | **一类户唯一性校验规则** | `AccountServiceImpl.openAccount`内联校验；`AccountMapper.countActiveClassOneAccountByCustomerId` | 1. 规则放宽（如允许有多个一类户但需审批）。<br>2. 规则收紧（如与客户年龄、职业挂钩）。 |
| **EXT-005** | **账户编号生成策略** | `AccountServiceImpl.generateAccountNo` (私有方法) | 1. 按分行、产品类型生成不同前缀的账号。<br>2. 增加校验位算法。<br>3. 使用分布式号段生成器。 |
| **EXT-006** | **产品可售性校验扩展** | `AccountServiceImpl.openAccount`内联校验；`ProductRepositoryImpl.findByProductCode` | 1. 增加销售时间窗口、客户等级、地域限制等校验维度。<br>2. 实现产品捆绑销售逻辑。 |
| **EXT-007** | **账户详情聚合查询扩展** | `AccountServiceImpl.getAccountDetail` | 1. 聚合更多信息：最近交易、持有理财、贷款额度、利率信息。<br>2. 根据调用方角色返回差异化信息。 |
| **EXT-008** | **产品信息获取扩展** | `ProductRepositoryImpl.findByProductCode` | 1. 增加多级缓存（本地缓存、Redis）。<br>2. 从外部产品中心实时获取信息。<br>3. 支持产品灰度发布。 |
| **EXT-009** | **幂等控制框架接入** | `IdempotentRecordMapper`；所有写接口 | 1. 设计统一幂等切面（AOP），自动处理`request_id`。<br>2. 为`createCustomer`、`openAccount`及未来所有交易接口接入幂等。 |
| **EXT-010** | **交易流水记录与查询扩展** | `TransactionMapper`；未来交易服务 | 1. 实现存款、取款、转账等服务，调用`TransactionMapper.insert`记录流水。<br>2. 扩展流水字段：渠道、手续费、业务摘要、外部流水号。<br>3. 实现复杂交易查询服务。 |
| **EXT-011** | **状态机引擎引入** | 所有状态字段（需新建） | 1. 定义`CustomerStatusEnum`、`AccountStatusEnum`等枚举。<br>2. 引入状态机框架（如Spring StateMachine）管理状态流转。 |
| **EXT-012** | **TAE记账服务接入** | 需全新设计 | 1. 定义TAE场景码、请求/响应DTO。<br>2. 实现TAE调用客户端和回调处理器。<br>3. 将业务流水(`t_transaction`)与TAE记账流水关联。 |

## 8. 待确认
以下信息在提供的源码切片中无法确认，需结合完整代码库或配置进行核实。

1.  **事务管理细节**：虽然`openAccount`和`createCustomer`有`@Transactional`，但事务传播行为、隔离级别、以及`application.yml`中的完整事务配置**待确认**。
2.  **主键生成具体实现**：`generateCustomerId`和`generateAccountNo`的私有方法逻辑已明确，但生成规则中“时间”的格式（如`yyyyMMddHHmmss`）**待确认**。
3.  **完整的API定义**：`CustomerController`和`AccountController`的完整代码（如请求DTO、参数校验注解）**待确认**。
4.  **配置与依赖**：`application.yml`配置文件内容，包括数据源、MyBatis、事务管理器等配置**待确认**。
5.  **枚举定义**：是否有独立的枚举类定义`ACTIVE`、`ON_SALE`、`CLASS_I`等常量，还是硬编码在代码中**待确认**。
6.  **缓存使用**：系统是否使用了缓存（如`@Cacheable`），**待确认**。
7.  **日志与监控**：是否有统一的审计日志、操作日志记录，**待确认**。
8.  **打包与部署**：项目结构是单体还是微服务模块，**待确认**（当前分析倾向为单体）。