# 系统基线知识摘要

## 1. 系统总体判断
当前系统是一个银行核心系统的**基础框架或POC版本**，主要实现了客户、账户、产品的**基础管理功能**（增、查）。系统为基于Spring Boot的HTTP REST单体应用，采用Repository模式进行数据访问。**关键的资金类交易（如存款、取款、转账）及其核心的TAE（记账引擎）集成、完整的幂等控制、状态机、补偿冲正等均未实现**，仅预留了相关的数据表结构。系统处于“有骨架，无血肉”的状态，为后续实现完整的银行业务流程提供了基础数据模型和扩展框架。

## 2. 业务能力清单
| 能力编号 | 能力名称 | 核心证据文件 | 说明 |
|---|---|---|---|
| CAP-001 | 客户信息管理 | `CustomerController.java`, `CustomerServiceImpl.java`, `CustomerMapper.xml` | 创建零售客户，校验证件号唯一性，生成客户号并持久化。 |
| CAP-002 | 账户开立 | `AccountController.java`, `AccountServiceImpl.java`, `AccountMapper.xml` | 为客户开立账户，校验客户存在、产品可售、一类户唯一性规则，生成账号并持久化。 |
| CAP-003 | 账户信息查询 | `AccountController.java`, `AccountServiceImpl.java` | 聚合查询账户详情，包含关联的客户和产品信息。 |
| CAP-004 | 产品信息查询 | `ProductRepositoryImpl.java`, `ProductMapper.xml` | 根据产品代码查询产品配置信息，用于开户等场景的规则校验。 |
| CAP-005 | 交易流水记录（结构预留） | `TransactionEntity.java`, `TransactionMapper.xml` | 定义了交易流水表`t_transaction`的结构和Mapper，**当前未在业务逻辑中使用**。 |
| CAP-006 | 幂等控制（结构预留） | `IdempotentRecordEntity.java`, `IdempotentRecordMapper.xml` | 定义了幂等记录表`t_idempotent_record`的结构和Mapper，**当前未在业务逻辑中使用**。 |
| CAP-007 | 系统健康检查 | `HealthController.java` | 提供应用健康状态检查接口。 |

## 3. 入口层清单
系统当前为基于Spring Boot的HTTP REST服务，未发现MQ消费者、定时任务等异步入口。
| 类型 | 路径 | 类 | 方法 | 对应能力 | 备注 |
|---|---|---|---|---|---|
| HTTP REST | `POST /api/v1/customers` | `CustomerController` | `createCustomer` | CAP-001 | 客户创建入口 |
| HTTP REST | `POST /api/v1/accounts/open` | `AccountController` | `openAccount` | CAP-002 | 账户开立入口 |
| HTTP REST | `GET /api/v1/accounts/{accountNo}` | `AccountController` | `getAccountDetail` | CAP-003 | 账户查询入口 |
| HTTP REST | `GET /health` | `HealthController` | `health` | CAP-007 | 健康检查入口 |
| 全局异常处理 | N/A | `GlobalExceptionHandler` | `handleBizException`等 | 通用 | 统一处理所有HTTP入口的异常。 |

## 4. 应用服务层/主流程清单
核心业务逻辑集中在Service层，采用Repository模式封装数据访问。
| 组件类型 | 类/文件 | 关键方法 | 职责与流程 | 对应能力 |
|---|---|---|---|---|
| **业务服务** | `CustomerServiceImpl` | `createCustomer` | 1. 校验证件号唯一性(`CustomerRepository.existsByCertNo`)。<br>2. 生成客户号(`generateCustomerId`)。<br>3. 组装`CustomerEntity`并设置默认状态(`ACTIVE`)和风险等级(`R2`)。<br>4. 持久化(`CustomerRepository.save`)。 | CAP-001 |
| **业务服务** | `AccountServiceImpl` | `openAccount` | 1. 校验客户存在(`CustomerRepository.findByCustomerId`)。<br>2. 校验产品可售(`ProductRepository.findByProductCode`)。<br>3. 校验一类户唯一性(`AccountRepository.existsActiveClassOneAccount`)。<br>4. 生成账号(`generateAccountNo`)。<br>5. 组装`AccountEntity`并设置默认状态(`ACTIVE`)。<br>6. 持久化(`AccountRepository.save`)。 | CAP-002 |
| **业务服务** | `AccountServiceImpl` | `getAccountDetail` | 1. 获取账户实体(`AccountRepository.findByAccountNo`)。<br>2. 获取客户实体(`CustomerRepository.findByCustomerId`)。<br>3. 获取产品实体(`ProductRepository.findByProductCode`)。<br>4. 聚合三者信息返回。 | CAP-003 |

## 5. 规则层/策略层清单
当前系统无独立的规则引擎或策略层，规则校验内嵌在Service方法中。
| 规则名称 | 校验点 | 实现位置 | 说明 |
|---|---|---|---|
| **证件号唯一性规则** | 创建客户时，证件号不能重复。 | `CustomerServiceImpl.createCustomer` -> `CustomerRepository.existsByCertNo` | 通过`t_customer.cert_no`唯一索引保证。 |
| **客户存在性规则** | 开户时，客户必须存在且状态有效。 | `AccountServiceImpl.openAccount` -> `CustomerRepository.findByCustomerId` | 直接查询客户表。 |
| **产品可售性规则** | 开户时，产品必须存在且销售状态为`ON_SALE`。 | `AccountServiceImpl.openAccount` -> `ProductRepository.findByProductCode` | 检查`t_product.sale_status`字段。 |
| **一类户唯一性规则** | 一个客户只能有一个状态为`ACTIVE`的一类户。 | `AccountServiceImpl.openAccount` -> `AccountRepository.existsActiveClassOneAccount` -> `AccountMapper.countActiveClassOneAccountByCustomerId` | SQL逻辑：`WHERE customer_id = #{customerId} AND account_status = 'ACTIVE' AND account_level = 'CLASS_I'`。 |

## 6. TAE 记账相关设计摘要
**当前源码中未发现任何与TAE（记账引擎）相关的代码、配置、场景码或回调处理逻辑。**
- **无TAE服务调用接口**（如RPC、HTTP Client定义）。
- **无TAE回调处理器**（Controller或Listener）。
- **实体类中无TAE流水号、场景码、记账状态等字段**。
- **结论**：这是一个基础框架演示，**所有涉及资金余额变动（如存款、取款、转账）的交易流程均未实现**。后续开发需全新设计TAE集成层，包括场景码定义、请求/响应DTO、调用客户端、异步回调处理及与业务流水(`t_transaction`)的关联。

## 7. 数据模型摘要
系统包含五张核心表，表结构通过`schema.sql`和MyBatis Mapper定义。
| 实体/表名 | 关键字段（用途） | 关联关系 | 说明 |
|---|---|---|---|
| **`CustomerEntity` (`t_customer`)** | `customer_id` (PK，客户号), `cert_no` (唯一索引，证件号), `customer_status` (状态), `risk_level` (风险等级) | 一对多 `t_account` | 客户主数据。状态和风险等级为`String`类型，无枚举约束。 |
| **`ProductEntity` (`t_product`)** | `product_code` (PK，产品代码), `sale_status` (销售状态), `account_level` (账户等级，如`CLASS_I`), `product_type`, `currency` | 一对多 `t_account` | 产品定义表。`sale_status`控制可售性，`account_level`用于一类户规则。 |
| **`AccountEntity` (`t_account`)** | `account_no` (PK，账号), `customer_id` (FK), `product_code` (FK), `balance` (余额), `account_status` (状态), `open_date` | 多对一 `t_customer`, `t_product` | 账户主表。余额为`BigDecimal`。状态为`String`类型。 |
| **`TransactionEntity` (`t_transaction`)** | `txn_id` (PK), `request_id` (唯一索引), `debit_account_no`, `credit_account_no`, `txn_type`, `txn_status`, `amount`, `txn_time` | 与`t_account`通过账号关联 | **预留结构**。包含借贷方、交易类型、状态、金额、时间等字段，可用于记录业务流水。`request_id`设计用于幂等。 |
| **`IdempotentRecordEntity` (`t_idempotent_record`)** | `request_id` (PK), `business_type`, `business_key`, `process_status`, `response_code`, `response_message` | 无 | **预留结构**。用于实现幂等控制，通过`request_id` + `business_type`标识唯一请求。 |

## 8. 状态机摘要
当前系统**无状态机引擎**，状态管理极为简单。
- **状态字段**：实体中存在状态字段（`customer_status`, `account_status`, `txn_status`, `process_status`），但均为`String`类型。
- **状态流转**：无状态枚举定义，无状态转换规则。创建实体时直接设置为默认值（如`ACTIVE`）。
- **结论**：状态管理是硬编码的，后续需要为每个业务实体定义状态枚举，并引入状态机（如Spring StateMachine）来管理复杂的状态流转和校验。

## 9. 幂等、事务、补偿、冲正、对账摘要
当前系统在这些资金交易核心领域仅有**基础的结构预留，缺乏完整的逻辑实现**。
| 方面 | 现状分析 | 证据与说明 |
|---|---|---|
| **幂等控制** | **表结构已设计，业务层未集成**。`t_idempotent_record`表和Mapper已就绪，但业务服务中**未调用**。HTTP接口也未要求传入`request_id`。 | `IdempotentRecordMapper.xml` 包含`insert`和`selectByRequestId`语句。业务服务中无相关代码。 |
| **本地事务** | **部分服务方法使用了声明式事务**。`AccountServiceImpl.openAccount`和`CustomerServiceImpl.createCustomer`方法上使用了`@Transactional`注解。 | 代码中可见`@Transactional`注解，保证了单数据库操作的事务性。具体传播行为和隔离级别**待确认**。 |
| **补偿** | **未实现**。未发现任何补偿交易（Compensation Transaction）相关的设计、表字段或服务方法。 | 无相关代码或表结构。 |
| **冲正** | **未实现**。未发现任何冲正（Reversal）相关的设计、表字段、状态或服务方法。 | 无相关代码或表结构。`txn_status`无“冲正”相关状态。 |
| **对账** | **仅有查询能力预留**。`TransactionMapper.xml`提供了按时间范围查询流水的SQL(`selectByTxnTimeBetween`)，但无对账任务、文件生成、差异处理等完整流程。 | 无定时Job或对账服务类。 |
| **分布式事务** | **未涉及**。当前为单体应用，无跨服务调用，因此未使用Seata等分布式事务方案。 | 无相关代码或配置。 |

## 10. 外部系统调用关系
基于当前源码分析，**系统未与任何外部系统（如TAE、短信平台、支付渠道）进行集成**。所有数据操作均在本地数据库完成。

## 11. 差异化扩展点清单
以下扩展点基于现有代码结构提炼，可用于指导后续差异化需求的详细设计。
| 扩展点编号 | 扩展点名称 | 关联文件/方法 | 扩展场景示例 |
|---|---|---|---|
| **EXT-001** | **客户创建规则扩展** | `CustomerServiceImpl.createCustomer` | 增加黑名单、反洗钱、渠道定制流程、创建后初始化等。 |
| **EXT-002** | **客户编号生成策略** | `CustomerServiceImpl.generateCustomerId` (私有方法) | 变更生成规则，接入全局序列服务。 |
| **EXT-003** | **账户开立规则扩展** | `AccountServiceImpl.openAccount` | 增加金额门槛、风险等级限制、联名开户、自动签约等。 |
| **EXT-004** | **一类户唯一性校验规则** | `AccountServiceImpl.openAccount`; `AccountMapper.countActiveClassOneAccountByCustomerId` | 规则放宽（如审批后多户）或收紧（与年龄职业挂钩）。 |
| **EXT-005** | **账户编号生成策略** | `AccountServiceImpl.generateAccountNo` (私有方法) | 按分行、产品类型生成前缀，增加校验位，使用分布式生成器。 |
| **EXT-006** | **产品可售性校验扩展** | `AccountServiceImpl.openAccount`; `ProductRepositoryImpl.findByProductCode` | 增加销售时间、客户等级、地域限制、捆绑销售等维度。 |
| **EXT-007** | **账户详情聚合查询扩展** | `AccountServiceImpl.getAccountDetail` | 聚合交易、理财、贷款、利率等信息，或根据角色返回差异化信息。 |
| **EXT-008** | **产品信息获取扩展** | `ProductRepositoryImpl.findByProductCode` | 增加多级缓存，接入外部产品中心，支持灰度发布。 |
| **EXT-009** | **幂等控制框架接入** | `IdempotentRecordMapper`；所有写接口 | 设计统一幂等切面（AOP），自动处理`request_id`。 |
| **EXT-010** | **交易流水记录与查询扩展** | `TransactionMapper`；未来交易服务 | 实现存款/取款/转账服务并记录流水，扩展流水字段，实现复杂查询。 |
| **EXT-011** | **状态机引擎引入** | 所有状态字段（需新建枚举） | 定义状态枚举，引入状态机框架管理状态流转。 |
| **EXT-012** | **TAE记账服务接入** | 需全新设计 | 定义场景码、DTO，实现TAE调用客户端和回调处理器，关联业务流水与TAE流水。 |

## 12. 代码组件映射
| 组件类型 | 包/类路径 (示例) | 职责 |
|---|---|---|
| **Controller** | `com.example.bank.controller.*Controller` | 接收HTTP请求，调用Service，返回统一响应(`ApiResponse`)。 |
| **Service** | `com.example.bank.service.impl.*ServiceImpl` | 实现核心业务逻辑，内嵌规则校验，调用Repository。 |
| **Repository** | `com.example.bank.repository.impl.*RepositoryImpl` | 封装数据访问逻辑，委托给Mapper。 |
| **Mapper** | `com.example.bank.mapper.*Mapper` (接口) | 定义数据访问接口。 |
| **Mapper XML** | `resources/mapper/*Mapper.xml` | 编写SQL语句，映射到实体。 |
| **Entity** | `com.example.bank.entity.*Entity` | 对应数据库表。 |
| **DTO** | `待确认` | 请求和响应数据传输对象。 |
| **Exception** | `com.example.bank.exception.BizException` | 业务异常。 |
| **Handler** | `com.example.bank.handler.GlobalExceptionHandler` | 全局异常处理器。 |

## 13. 待确认问题
1.  **事务管理细节**：`@Transactional`注解的具体传播行为、隔离级别，以及`application.yml`中的完整事务配置**待确认**。
2.  **主键生成具体格式**：`generateCustomerId`和`generateAccountNo`方法中“时间”的具体格式（如`yyyyMMddHHmmss`）**待确认**。
3.  **完整的API定义**：`CustomerController`和`AccountController`的完整代码，包括请求DTO、参数校验注解（如`@Valid`）**待确认**。
4.  **配置与依赖**：`application.yml`配置文件内容，包括数据源、MyBatis、事务管理器、服务器端口等配置**待确认**。
5.  **枚举定义**：是否有独立的枚举类定义`ACTIVE`、`ON_SALE`、`CLASS_I`、`R2`等常量，还是硬编码在代码中**待确认**。
6.  **缓存使用**：系统是否使用了缓存（如Spring `@Cacheable`，或Redis配置）**待确认**。
7.  **日志与监控**：是否有统一的审计日志、操作日志记录（如AOP切面），以及监控指标收集**待确认**。
8.  **打包与部署**：项目是单体应用还是微服务模块（当前分析倾向为单体），以及相关的Maven/Gradle模块结构**待确认**。