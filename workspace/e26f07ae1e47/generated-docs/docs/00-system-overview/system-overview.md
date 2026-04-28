# 银行核心系统（POC版）基线设计文档 - 系统总览

## 1. 系统定位

当前系统是一个**银行核心系统的概念验证（POC）版本**，旨在演示零售银行业务的基础框架和数据模型。系统实现了客户管理、产品管理、账户开立与查询等**基础管理功能**，为后续实现完整的存款、贷款、支付等资金类交易提供了**可扩展的骨架**。

**核心特点**：
- **业务范围**：聚焦于客户与账户的**生命周期管理**，不包含任何资金变动交易。
- **技术架构**：基于Spring Boot的单体RESTful应用，采用经典的Controller-Service-Repository分层架构。
- **集成状态**：**未与任何外部核心系统（如TAE记账引擎、支付渠道、短信平台）集成**。
- **成熟度**：处于“有骨架，无血肉”状态，关键的资金交易流程、状态机、分布式事务等均需后续开发。

## 2. 技术架构

### 2.1 整体架构视图
```
[HTTP Client] -> [Spring Boot Application] -> [Local Database]
       ↑                ↑
   REST API        Monolithic
```

### 2.2 技术栈明细
| 组件 | 技术选型 | 版本/配置待确认 |
|---|---|---|
| **开发框架** | Spring Boot | 待确认 |
| **Web容器** | 内嵌Tomcat | 待确认 |
| **数据持久化** | MyBatis 3.x | 待确认 |
| **数据库** | H2 / MySQL (基于`schema.sql`推断) | 待确认 |
| **依赖管理** | Maven | `pom.xml` |
| **API风格** | RESTful HTTP | JSON请求/响应 |
| **事务管理** | Spring声明式事务 (`@Transactional`) | 传播行为与隔离级别待确认 |
| **异常处理** | 全局异常处理器 (`GlobalExceptionHandler`) | 统一返回`ApiResponse` |

### 2.3 工程结构
```
poc/
├── src/main/java/com/bank/poc/
│   ├── RetailBankPocApplication.java          # Spring Boot主类
│   ├── controller/                            # HTTP入口层
│   │   ├── AccountController.java            # 账户相关API
│   │   ├── CustomerController.java           # 客户相关API
│   │   └── HealthController.java             # 健康检查API
│   ├── service/                              # 业务逻辑层
│   │   ├── AccountService.java
│   │   ├── CustomerService.java
│   │   └── impl/                             # 服务实现
│   │       ├── AccountServiceImpl.java
│   │       └── CustomerServiceImpl.java
│   ├── repository/                           # 仓储层（数据访问抽象）
│   │   ├── AccountRepository.java
│   │   ├── CustomerRepository.java
│   │   ├── ProductRepository.java
│   │   └── impl/                             # 仓储实现
│   │       ├── AccountRepositoryImpl.java
│   │       ├── CustomerRepositoryImpl.java
│   │       └── ProductRepositoryImpl.java
│   ├── mapper/                               # MyBatis Mapper接口
│   │   ├── AccountMapper.java
│   │   ├── CustomerMapper.java
│   │   ├── IdempotentRecordMapper.java
│   │   ├── ProductMapper.java
│   │   └── TransactionMapper.java
│   ├── entity/                               # 数据库实体类
│   │   ├── AccountEntity.java
│   │   ├── CustomerEntity.java
│   │   ├── IdempotentRecordEntity.java
│   │   ├── ProductEntity.java
│   │   └── TransactionEntity.java
│   ├── dto/                                  # 数据传输对象
│   │   ├── request/                          # 请求DTO
│   │   │   ├── CreateCustomerRequest.java
│   │   │   └── OpenAccountRequest.java
│   │   └── response/                         # 响应DTO
│   │       ├── AccountDetailResponse.java
│   │       ├── CreateCustomerResponse.java
│   │       └── OpenAccountResponse.java
│   ├── common/                               # 公共组件
│   │   ├── api/ApiResponse.java              # 统一API响应
│   │   ├── exception/BizException.java       # 业务异常
│   │   └── config/GlobalExceptionHandler.java # 全局异常处理
│   └── (其他包如util、enums等待确认)
└── src/main/resources/
    ├── application.yml                       # 应用配置（内容待确认）
    ├── schema.sql                            # 数据库表结构DDL
    ├── data.sql                              # 初始数据
    └── mapper/                               # MyBatis XML映射文件
        ├── AccountMapper.xml
        ├── CustomerMapper.xml
        ├── IdempotentRecordMapper.xml
        ├── ProductMapper.xml
        └── TransactionMapper.xml
```

## 3. 分布式调用方式

**当前系统为单体应用，无跨服务分布式调用。**

- **外部系统集成**：无。未发现TAE、短信、支付渠道等外部服务的客户端或配置。
- **内部服务间调用**：通过本地Java方法调用（Controller -> Service -> Repository）。
- **异步通信**：未使用消息队列（MQ），无消费者或生产者代码。
- **定时任务**：未发现`@Scheduled`注解或Job类。

**后续扩展方向**：
- 资金交易需集成TAE，可能采用**同步HTTP/RPC调用**或**异步消息+回调**。
- 通知类功能可引入MQ（如RocketMQ、Kafka）。
- 批处理任务需引入定时任务框架。

## 4. 核心模块

### 4.1 客户管理模块 (`CAP-001`)
**定位**：零售客户主数据管理。
**核心流程**：创建客户 -> 校验证件唯一性 -> 生成客户号 -> 持久化。
**关键组件**：
- 入口：`CustomerController.createCustomer` (`POST /api/v1/customers`)
- 服务：`CustomerServiceImpl.createCustomer`
- 仓储：`CustomerRepository.existsByCertNo`, `CustomerRepository.save`
- 数据：`CustomerEntity` (`t_customer`表)
**状态管理**：客户状态(`customer_status`)硬编码为`ACTIVE`，无状态机。

### 4.2 账户管理模块 (`CAP-002`, `CAP-003`)
**定位**：账户生命周期管理（开立、查询）。
**核心流程**：开户 -> 校验客户/产品/一类户规则 -> 生成账号 -> 持久化。
**关键组件**：
- 入口：`AccountController.openAccount` (`POST /api/v1/accounts/open`)
- 服务：`AccountServiceImpl.openAccount`
- 规则校验：
  - 客户存在性：`CustomerRepository.findByCustomerId`
  - 产品可售性：`ProductRepository.findByProductCode` (检查`sale_status = 'ON_SALE'`)
  - 一类户唯一性：`AccountRepository.existsActiveClassOneAccount` -> `AccountMapper.countActiveClassOneAccountByCustomerId`
- 数据：`AccountEntity` (`t_account`表), `ProductEntity` (`t_product`表)

### 4.3 产品管理模块 (`CAP-004`)
**定位**：产品配置信息查询。
**核心组件**：
- 仓储：`ProductRepositoryImpl.findByProductCode`
- 数据：`ProductEntity` (`t_product`表)
**用途**：为开户等场景提供产品规则（账户等级、币种等）。

### 4.4 基础框架模块
**定位**：提供可扩展的支撑结构。
**包含**：
- **幂等控制结构**：`IdempotentRecordEntity`与`IdempotentRecordMapper`已定义，**但业务层未集成**。
- **交易流水结构**：`TransactionEntity`与`TransactionMapper`已定义，**但业务层未使用**。
- **统一响应**：`ApiResponse`。
- **异常处理**：`BizException`与`GlobalExceptionHandler`。

## 5. 关键交易链路分析

### 5.1 账户开立交易链路（当前已实现）
```
1. HTTP请求 -> POST /api/v1/accounts/open
   ├── Controller: AccountController.openAccount(OpenAccountRequest)
   ├── Service: AccountServiceImpl.openAccount(request)
   │   ├── 校验1: 客户存在 (CustomerRepository.findByCustomerId)
   │   ├── 校验2: 产品可售 (ProductRepository.findByProductCode)
   │   ├── 校验3: 一类户唯一性 (AccountRepository.existsActiveClassOneAccount)
   │   ├── 生成账号: generateAccountNo() [规则: 62+时间+6位随机数]
   │   ├── 组装实体: AccountEntity
   │   └── 持久化: AccountRepository.save (带@Transactional)
   └── 响应: ApiResponse<OpenAccountResponse>
```
**涉及修改点**：
- 规则扩展：修改`AccountServiceImpl.openAccount`方法内的校验逻辑。
- 账号生成：修改`AccountServiceImpl.generateAccountNo`私有方法。
- 产品校验：修改`ProductRepositoryImpl.findByProductCode`逻辑或`t_product`表数据。

### 5.2 客户创建交易链路（当前已实现）
```
1. HTTP请求 -> POST /api/v1/customers
   ├── Controller: CustomerController.createCustomer(CreateCustomerRequest)
   ├── Service: CustomerServiceImpl.createCustomer(request)
   │   ├── 校验: 证件号唯一性 (CustomerRepository.existsByCertNo)
   │   ├── 生成客户号: generateCustomerId() [规则: C+时间+4位随机数]
   │   ├── 组装实体: CustomerEntity (设置status=ACTIVE, risk_level=R2)
   │   └── 持久化: CustomerRepository.save (带@Transactional)
   └── 响应: ApiResponse<CreateCustomerResponse>
```

### 5.3 资金类交易链路（**未实现，需全新设计**）
**以存款为例，示意需补充的完整流程**：
```
1. HTTP请求 -> POST /api/v1/transactions/deposit
   ├── 幂等检查: 通过IdempotentRecordMapper检查request_id (需新增)
   ├── 记录业务流水: TransactionMapper.insert (状态=INIT) (需新增)
   ├── 调用TAE记账: TaeClient.postAccounting(sceneCode, accountingRequest) (需新增)
   │   ├── 同步响应: 处理TAE返回(成功/失败)
   │   └── 异步回调: TaeCallbackController.handleCallback (需新增)
   ├── 更新业务流水状态: TransactionMapper.updateStatus (需新增)
   ├── 更新账户余额: AccountMapper.updateBalance (需新增，需与TAE对账)
   ├── 更新幂等记录: IdempotentRecordMapper.update (需新增)
   └── 异常处理: 冲正、补偿流程 (需新增)
```
**关键缺口**：
1. **TAE集成层缺失**：无场景码定义、无TAE客户端、无回调处理器。
2. **状态机缺失**：交易状态(`txn_status`)流转无管理。
3. **幂等框架缺失**：虽有表结构，但无切面或工具类集成。
4. **补偿冲正缺失**：无异常后的回滚机制。
5. **对账流程缺失**：无定时任务比对业务流水与TAE流水。

## 6. 资金类流程关键设计现状

| 维度 | 现状 | 证据位置 | 对后续开发的影响 |
|---|---|---|---|
| **TAE记账** | **完全缺失** | 无相关代码、配置、DTO | 任何资金交易需求都需全新设计TAE集成层。 |
| **状态机** | **简单字符串字段** | `*Entity`中的`*_status`字段均为String | 需定义枚举，引入状态机框架管理流转。 |
| **幂等** | **表结构就绪，逻辑未集成** | `IdempotentRecordEntity`, `IdempotentRecordMapper.xml` | 需设计统一切面(`@Idempotent`)并在所有写接口应用。 |
| **本地事务** | **声明式事务已应用** | `@Transactional` on `openAccount`, `createCustomer` | 当前单库操作有保障。跨服务/TAE时需升级为分布式事务。 |
| **补偿** | **无** | 无补偿交易表、字段或服务 | 需设计补偿流水表及补偿触发机制。 |
| **冲正** | **无** | `txn_status`无“REVERSAL”等状态 | 需在交易流水设计中增加冲正状态和关联原交易。 |
| **对账** | **仅有流水查询能力** | `TransactionMapper.selectByTxnTimeBetween` | 需开发对账Job、文件生成器、差异处理服务。 |

## 7. 后续差异化需求开发指引

当接到新需求时，可按以下矩阵定位修改点：

| 需求类型 | 主要修改层 | 关键类/文件 | 注意事项 |
|---|---|---|---|
| **新增业务规则** (如开户增加风险校验) | Service层 | `AccountServiceImpl.openAccount` | 在现有校验链中插入新规则。 |
| **修改现有规则** (如放宽一类户限制) | Service层 + Mapper | `AccountServiceImpl.openAccount`<br>`AccountMapper.countActiveClassOneAccountByCustomerId` | 确保SQL逻辑与Java逻辑同步更新。 |
| **新增API接口** | Controller + Service + DTO | `*Controller.java`, `*Service.java`, `dto/` | 遵循现有REST风格和`ApiResponse`包装。 |
| **修改编号生成规则** | Service层 (私有方法) | `*ServiceImpl.generate*`方法 | 注意并发场景下的唯一性。 |
| **接入外部系统** (如TAE) | 全新模块 | 需新建`tae/client/`, `tae/callback/`, `config/` | 设计场景码枚举、请求/响应DTO、重试与降级策略。 |
| **实现资金交易** (存款/转账) | 全新Service + 集成TAE + 幂等 | 需新建`TransactionService`，并集成幂等切面、TAE客户端 | **这是最大缺口**，需完整设计事务边界、状态流转、异常处理。 |
| **增加状态流转** | Entity (改枚举) + 状态机框架 | 需定义状态枚举，可能引入Spring StateMachine | 避免硬编码状态字符串。 |
| **实现定时任务** | 全新Job类 + 配置 | 需使用`@Scheduled`，并考虑集群部署下的竞争。 |  |
| **实现MQ消费** | 全新Listener类 | 需配置MQ连接，处理消息幂等。 |  |

## 8. 待确认事项

1.  **配置细节**：`application.yml`中的数据库连接池、MyBatis、事务管理器详细配置。
2.  **枚举定义**：是否存在独立的枚举类定义`ACTIVE`、`ON_SALE`、`CLASS_I`等常量。
3.  **主键生成细节**：`generateCustomerId`和`generateAccountNo`方法中“时间”部分的具体格式。
4.  **API完整契约**：`CustomerController`和`AccountController`的完整代码，包括URL路径、参数校验注解。
5.  **缓存策略**：是否使用Spring Cache或Redis进行缓存优化。
6.  **日志与监控**：是否有统一的审计日志、性能监控埋点。
7.  **打包部署**：项目是作为单体应用直接部署，还是某个微服务模块的一部分。

---
**文档版本**：1.0  
**基线对应代码版本**：POC初始版本  
**摘要**：本系统为银行核心业务提供了基础数据模型和管理框架，但所有涉及资金记账、复杂状态流转、分布式协调的核心功能均待实现。后续开发应以本基线文档为指导，在现有结构上增量构建。