# 源码片段分析摘要

## 1. 业务能力

| 能力编号建议 | 能力名称 | 证据文件 | 说明 |
|---|---|---|---|
| BC-001 | 客户信息管理 | `CustomerServiceImpl.java`, `CustomerController.java` | 创建客户，校验证件唯一性，生成客户号。 |
| BC-002 | 产品信息查询 | `ProductRepository.java`, `ProductMapper.xml` | 提供产品信息查询能力，支持按产品代码、销售状态查询。 |
| BC-003 | 账户开立 | `AccountServiceImpl.java`, `AccountController.java` | 为客户开立账户，涉及客户、产品、一类户唯一性校验，生成账号。 |
| BC-004 | 账户信息查询 | `AccountServiceImpl.java`, `AccountController.java` | 查询账户详情，聚合客户、产品信息。 |
| BC-005 | 交易流水记录 | `TransactionMapper.xml` | 提供交易流水（`t_transaction`）的增删改查能力，支持按交易ID、请求ID、账号、时间范围查询。 |
| BC-006 | 幂等性控制 | `IdempotentRecordMapper.xml` | 提供幂等记录（`t_idempotent_record`）的创建和查询能力，用于防止重复请求。 |

## 2. 入口

| 类型 | 路径/Topic/Job | 类 | 方法 | 对应能力 |
|---|---|---|---|---|
| HTTP API | `/api/customer` (推测) | `CustomerController` (推测) | `createCustomer` (推测) | BC-001 |
| HTTP API | `/api/account/open` (推测) | `AccountController` (推测) | `openAccount` (推测) | BC-003 |
| HTTP API | `/api/account/{accountNo}` (推测) | `AccountController` (推测) | `getAccountDetail` (推测) | BC-004 |
| 待确认 | 无 | 无 | 无 | 当前源码未展示Controller、MQ、Job等具体入口类。 |

## 3. 核心组件

| 组件类型 | 类/文件 | 方法 | 职责 | 对应能力 |
|---|---|---|---|---|
| 核心服务 | `AccountServiceImpl` | `openAccount` | 开户主流程，串联客户、产品校验，生成账号并落库。 | BC-003 |
| 核心服务 | `AccountServiceImpl` | `getAccountDetail` | 账户详情查询主流程，聚合客户、产品信息。 | BC-004 |
| 核心服务 | `CustomerServiceImpl` | `createCustomer` | 创建客户主流程，校验证件唯一性，生成客户号并落库。 | BC-001 |
| 仓储接口 | `AccountRepository` | `existsActiveClassOneAccount` | 校验客户是否存在激活状态的一类户，实现一类户唯一性规则。 | BC-003 |
| 仓储接口 | `CustomerRepository` | `existsByCertNo` | 校验证件号是否已存在，实现客户唯一性规则。 | BC-001 |
| 数据访问 | `AccountMapper.xml` | `countActiveClassOneAccountByCustomerId` | 执行SQL，统计客户名下激活的一类户数量。 | BC-003 |
| 数据访问 | `CustomerMapper.xml` | `countByCertNo` | 执行SQL，统计指定证件号的客户数量。 | BC-001 |
| 数据访问 | `ProductMapper.xml` | `selectByProductCode` | 执行SQL，根据产品代码查询产品信息。 | BC-002 |
| 规则/校验 | `AccountServiceImpl` | `openAccount` 内联校验 | 校验客户存在、产品存在、产品可售、一类户唯一性。 | BC-003 |
| 规则/校验 | `CustomerServiceImpl` | `createCustomer` 内联校验 | 校验证件号唯一性。 | BC-001 |
| 编号生成器 | `AccountServiceImpl` | `generateAccountNo` (private) | 生成账号（规则：62+时间+6位随机数）。 | BC-003 |
| 编号生成器 | `CustomerServiceImpl` | `generateCustomerId` (private) | 生成客户号（规则：C+时间+4位随机数）。 | BC-001 |

## 4. TAE 相关

| 类型 | 文件 | 方法/字段 | 说明 |
|---|---|---|---|
| 无 | 无 | 无 | **当前源码片段中未发现任何与TAE（记账引擎）相关的调用、回调、场景码或流水号字段。** 这是一个POC演示系统，可能未集成核心记账功能。 |

## 5. 数据模型

| 表/实体/Mapper | 文件 | 关键字段 | 说明 |
|---|---|---|---|
| `t_customer` / `CustomerEntity` / `CustomerMapper` | `schema.sql`, `CustomerEntity.java`, `CustomerMapper.xml` | `customer_id`(主键), `cert_no`(唯一索引), `customer_status`, `risk_level` | 客户主表。`customer_status`标识客户状态（如ACTIVE）。`risk_level`标识风险等级（默认R2）。 |
| `t_product` / `ProductEntity` / `ProductMapper` | `schema.sql`, `ProductEntity.java`, `ProductMapper.xml` | `product_code`(主键), `sale_status`, `account_level`, `product_type`, `currency` | 产品定义表。`sale_status`控制可售性（ON_SALE/OFF_SALE）。`account_level`标识账户等级（CLASS_I/II/III）。 |
| `t_account` / `AccountEntity` / `AccountMapper` | `schema.sql`, `AccountEntity.java`, `AccountMapper.xml` | `account_no`(主键), `customer_id`(索引), `product_code`, `balance`, `account_status`, `open_date` | 账户主表。`balance`为账户余额。`account_status`标识账户状态（如ACTIVE）。与客户、产品关联。 |
| `t_transaction` / `TransactionEntity` / `TransactionMapper` | `schema.sql`, `TransactionEntity.java`, `TransactionMapper.xml` | `txn_id`(主键), `request_id`(唯一索引), `debit_account_no`, `credit_account_no`, `txn_type`, `txn_status`, `amount`, `txn_time` | 交易流水表。记录借贷账号、交易类型、状态、金额、时间。`request_id`用于幂等和关联。 |
| `t_idempotent_record` / `IdempotentRecordEntity` / `IdempotentRecordMapper` | `schema.sql`, `IdempotentRecordEntity.java`, `IdempotentRecordMapper.xml` | `request_id`(主键), `business_type`, `business_key`, `process_status`, `response_code`, `response_message` | 幂等性记录表。通过`request_id`和`business_type`保证请求唯一性，记录处理状态和结果。 |

## 6. 状态、幂等、补偿、冲正、对账

| 类型 | 文件 | 方法/字段 | 说明 |
|---|---|---|---|
| **状态机** | `AccountEntity.java`, `CustomerEntity.java`, `TransactionEntity.java` | `account_status`, `customer_status`, `txn_status` | 实体中包含状态字段，但**当前源码未展示明确的状态机流转逻辑**（如状态枚举、状态转换规则）。 |
| **幂等** | `IdempotentRecordMapper.xml`, `schema.sql` | `t_idempotent_record`表，`request_id`唯一索引 | 设计了幂等表结构，通过`request_id`作为唯一键防止重复请求。但**当前Service实现中未调用此表**。 |
| **补偿** | 无 | 无 | 未发现补偿交易（Reverse/Compensate）相关的逻辑、表结构或服务。 |
| **冲正** | 无 | 无 | 未发现冲正（Cancel/Correction）相关的逻辑、表结构或服务。 |
| **对账** | `TransactionMapper.xml` | `selectByTxnTimeBetween` | 提供了按时间范围查询交易流水的能力，**这可以用于对账文件生成**，但未发现完整的对账流程（如渠道对账、清算对账）。 |
| **事务** | `AccountServiceImpl.java`, `CustomerServiceImpl.java` | `@Transactional`注解 | 在`openAccount`和`createCustomer`方法上使用了Spring声明式事务，保证数据库操作的原子性。 |

## 7. 扩展点

| 扩展点编号建议 | 扩展点名称 | 文件 | 方法 | 适用差异需求 |
|---|---|---|---|---|
| EP-001 | **账户编号生成策略** | `AccountServiceImpl.java` | `generateAccountNo` (private) | 不同分行、产品、渠道需要不同的账号生成规则（如前缀、长度、校验位）。 |
| EP-002 | **客户编号生成策略** | `CustomerServiceImpl.java` | `generateCustomerId` (private) | 客户号生成规则变更（如引入机构号、序列号）。 |
| EP-003 | **一类户唯一性校验规则** | `AccountServiceImpl.java` | `openAccount` 内联校验逻辑 | 规则扩展，如“允许有多个一类户但需审批”、“一类户与客户风险等级挂钩”。 |
| EP-004 | **产品可售性校验规则** | `AccountServiceImpl.java` | `openAccount` 内联校验逻辑 | 扩展校验维度，如销售时间、客户等级、地域限制、捆绑销售等。 |
| EP-005 | **客户风险等级默认值规则** | `CustomerServiceImpl.java` | `createCustomer` 内联赋值逻辑 | 根据客户属性（如证件类型、职业）动态计算默认风险等级。 |
| EP-006 | **账户详情聚合查询** | `AccountServiceImpl.java` | `getAccountDetail` | 需要返回更多关联信息时（如客户地址、产品利率），可扩展聚合逻辑。 |
| EP-007 | **交易流水查询条件** | `TransactionMapper.xml` | 各类`select`语句 | 支持更复杂的交易查询，如多账户、多状态、金额区间、关联业务单号等。 |

## 8. 待确认

- **TAE集成情况**：系统是否与TAE集成？资金类交易（存款、取款、转账）的记账入口在哪里？`t_transaction`表是业务流水还是会计流水？
- **状态机明确定义**：`account_status`、`customer_status`、`txn_status`有哪些枚举值？状态之间的转换条件和驱动方是什么？
- **幂等框架集成**：`t_idempotent_record`表是否有配套的切面（AOP）或工具类在Service层自动使用？还是需要手动调用？
- **分布式事务方案**：跨服务调用（如调用TAE）时，如何保证事务一致性？是否使用了Seata、MQ事务消息等方案？
- **补偿与冲正机制**：对于失败的交易（如记账失败），系统是否有自动或手动的冲正流程？`txn_status`是否包含“冲正”状态？
- **对账全流程**：除查询流水外，对账的触发时机（定时Job？）、对账文件生成、差异处理等流程是否实现？
- **Controller及API定义**：具体的HTTP API路径、请求/响应DTO的完整结构、接口幂等性如何保证（如通过`request_id`）？
- **MQ与定时任务**：系统是否有异步消息处理（如开户成功通知）或定时任务（如日终批处理）？源码中未体现。