# 阶段 0：工程基线打通与数据库落地（实施文档）

> 约定：知识库后端每个实施阶段，都在本目录（`docs/`）落一份实施文档，记录目标、动作、验证标准与执行结果。

## 元信息

| 项         | 内容                                                    |
|------------|---------------------------------------------------------|
| 阶段       | 0（工程基线）                                           |
| 状态       | 已完成（封档 2026-08-31）                               |
| 方案依据   | `docs/知识库一期技术方案设计.html` 第 6.3 / 6.6 / 20 章 |
| 方案定稿日 | 2026-08-27                                              |
| 执行人     | 用户（自行执行）                                        |
| 执行日期   | 2026-08-31                                              |

## 1. 目标与范围

把后端工程从「3 模块空骨架」变成「可运行基线」：

- Maven 四模块齐：common / api / biz / **worker**（设计文档 6.3）；
- 本地 MySQL 建库并建立**按需迁移惯例**：表不一次性全建，只在实现步骤需要时新增迁移文件（设计文档 20.2 为字段级定义唯一来源）；
- 服务接通数据源，启动无数据源异常；
- 基线通过验证后 git 提交。

**不包含**：任何业务功能（B01–B10）、领域代码、前端。

## 2. 前置确认（已核实的环境事实）

| # | 事实                                                                                                                                                                                                                                             | 证据 / 来源                             |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| 1 | 数据源经测试 Nacos 的 `knowledge-biz.yml` 下发：测试主库 `mysql-master-test.ahyzc.com` / 库 `knowledge` / 账号 `yzc_soft`（MYSQL_HOST/USER/PWD/DB 等环境变量可覆盖）；测试 Nacos 可达                                                            | Nacos 本地快照 + 启动日志（2026-08-31） |
| 2 | 平台无 Flyway 惯例：整个 backend 无任何 pom 引入 flyway；zlzx 的 `src/main/resources/sql/V...__name.sql` 仅为命名惯例，由人工执行                                                                                                                | 全 backend pom 搜索无 flyway 匹配       |
| 3 | 端口 4330 与 zlzx 本地联调端口冲突，本地同跑需覆盖                                                                                                                                                                                               | zlzx `application.yml` 与联调手册       |
| 4 | 父 pom `com.youzhicai:knowledge:5.1.0` 可解析（骨架已编译出 target）                                                                                                                                                                             | 本地构建产物                            |
| 5 | 冻结决策：DP-01（向量组件）/ DP-03（Embedding 模型）等评审项暂不裁定，做到 B07/B08 时再定                                                                                                                                                        | 用户确认（2026-08-27 会话）             |
| 6 | `spring.config.import` 导入方（本地 application.yml）属性优先于 Nacos 导入的共享配置                                                                                                                                                             | Spring Boot 配置优先级                  |
| 7 | 建表策略：**按需建表**——只在实现步骤需要该表时新增迁移文件；阶段 0 不建任何业务表                                                                                                                                                                | 用户确认（2026-08 会话）                |
| 8 | 启动失败修复决策（2026-08-31）：DataSource 接入后 `MybatisPlusConfiguration` 激活，其数据权限拦截器链需要 upms 的 `RemoteDataScopeService`（Feign）；解决方案 = biz pom 新增 `common-security` 依赖（传递引入 `knowledge-upms-api`），与平台对齐 | 用户执行并确认启动通过                  |

## 3. 任务清单

### 任务 1：补建 worker 模块

1. 新建 `knowledge-worker/pom.xml`：
   - parent：`com.youzhicai:knowledge:5.1.0`；packaging `jar`；Java 21（对齐 biz pom 第 18–23 行写法）；
   - 依赖仅 `knowledge-common`。Tika / POI / PDFBox / Lucene 等到 B02 / B08 阶段再引入。
2. 按 6.3 包分层建空目录：`pipeline / parser / chunking / embedding / indexing / evaluation`（空目录放 `.gitkeep`）。
3. 父 pom：`<modules>` 增加 `<module>knowledge-worker</module>`；`dependencyManagement` 增加 worker（照抄 common/api 条目写法）。
4. biz pom：增加 worker 依赖（依赖方向 biz → common + api + worker）。
5. 验证：根目录 `mvn compile` 通过。

### 任务 2：建库与迁移惯例（按需建表，不一次性建）

1. 本地 MySQL 建独立库 `knowledge`（utf8mb4 / InnoDB），**建空库即可**。
2. 建立 SQL 管理惯例（用户约定 2026-08-31，为后续步骤做准备）：
   - 位置：`knowledge-biz/src/main/resources/sql/knowledge.sql`——所有 SQL **统一追加到这一个文件**管理；
   - 每条语句块注明：来源（20.2 小节/表名/HTML 行号）、实现步骤（step-0X）、执行人、日期；
   - 人工执行（平台无 Flyway；暂不引入 Flyway，设计文档 6.7 未禁止，后续可另行评估）；
   - 已执行过的语句块不再重复执行，新增表只执行新追加的语句块。
3. **本期不建任何业务表**。需要哪张表时，由对应实现步骤（step-0X 文档）指定从 20.2 取该表的字段级 DDL，追加到统一 SQL 文件后人工执行。
4. 计划映射（供各步骤细化，届时以 step 文档为准）：

| 实现步骤                    | 届时新建的表（DDL 取自 20.2 对应小节）                                                                    |
|-----------------------------|-----------------------------------------------------------------------------------------------------------|
| 阶段 1 领域模型与知识库管理 | kb_knowledge_base、kb_audit_log                                                                           |
| 阶段 2 B01 文档输入         | kb_source_file、kb_file_result、kb_submit_log、kb_pipeline_task                                           |
| 阶段 2/3 解析与统一模型     | kb_stage_product、kb_stage_step_record                                                                    |
| 阶段 3 预处理与切片         | kb_stage_strategy_version（PREPROCESS/CHUNK）、kb_processing_profile_version、kb_chunk_set、kb_chunk      |
| 阶段 4 向量化与索引         | kb_embedding_set、kb_embedding_record、kb_stage_strategy_version（EMBED）、kb_index_set、kb_index_version |
| 阶段 5 检索与评测           | kb_retrieval_rule_version、kb_retrieval_log                                                               |
| 预留（一期不建）            | kb_evaluation_question、kb_experiment_run、kb_metric_result、kb_task                                      |

5. 验证：开发库已建、可连接；`sql/` 目录与命名惯例就绪。

### 任务 3：接通数据源

1. `KnowledgeApplication.java` 删除两个 exclude（`DataSourceAutoConfiguration`、`DruidDataSourceAutoConfigure`），类上注释已写明"接入数据库后必须移除"。
2. **实际执行口径（2026-08-31）**：数据源配置维护在测试 Nacos 的 `knowledge-biz.yml`（knowledge 命名空间，druid 前缀），本地 `application.yml` 不写 datasource。关键参数：测试主库 `mysql-master-test.ahyzc.com` / 库 `knowledge` / 账号 `yzc_soft`（MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PWD 环境变量可覆盖）；已开启 druid stat 监控页（/druid/*）与 wall 过滤器（multi-statement-allow）。
   （本小节原拟的「本地 yml 数据源块」方案未采用，以此为准。）
3. 端口：`application.yml` 定为 `server.port: 4338`（避开 zlzx 本地 4330），启动无需 -D 覆盖。
4. **已知问题与修复（2026-08-31 已闭环）**：接入数据源后启动报 `mybatisPlusInterceptor required a bean of type DataScopeInterceptor`。根因：`common-data` 的 `MybatisPlusConfiguration` 被数据源激活，其 `dataScopeInterceptor(RemoteDataScopeService)` 需要 `knowledge-upms-api` 的 Feign 接口 bean，而 knowledge 精简依赖缺失。修复：biz pom 新增 `common-security` 依赖（其传递引入 upms-api），启动通过。
5. **修复带来的后续影响（阶段 1 首验项，勿忘）**：
   - kb 全表无 `tenant_id` 字段，而平台租户/数据权限拦截器已随该依赖激活——阶段 1 首次写 Mapper 查询时必须验证 SQL 是否被注入 `tenant_id`/数据权限条件导致报错；如有冲突，再评估关停方案（如条件属性、自定义拦截器配置）；
   - `RemoteDataScopeService` 为运行时 Feign 调用（经 Nacos 调 upms），upms 不可达时数据权限查询可能失败——需在联调环境验证并明确超时/失败行为；
   - common-security 引入 OAuth 授权服务器相关自动装配，对知识库接口的鉴权影响在接口开放阶段（阶段 1/前端阶段）一并评估。

### 任务 4：验证与提交

1. 根目录 `mvn clean package -DskipTests`（或 IDE 构建）。
2. 带 DB 环境变量启动 `KnowledgeApplication`。
3. 检查：启动无数据源/建表报错；开发库表已就位；测试 Nacos 控制台能看到服务注册。
4. 通过后 git 提交基线。

## 4. 验收标准

- [x] `mvn compile` 通过，四模块齐全
- [x] 开发库 `knowledge` 已建、可连接；`sql/` 目录与统一 SQL 管理文件就绪（本期不建业务表）
- [x] 服务启动成功，无数据源相关异常
- [x] 基线已提交 git

## 5. 执行记录

| 任务                       | 执行结果                                                                                    | 日期       | 备注（报错/偏差）                                                                                                        |
|----------------------------|---------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------|
| 任务 1 worker 模块         | 完成：worker pom / AppTest / 父 pom（modules + dependencyManagement）/ biz pom 依赖全部就绪 | 2026-08-31 | 子包分层留待阶段 2                                                                                                       |
| 任务 2 建库与 SQL 管理文件 | 完成：库已建在测试主库（Druid 连接成功为证）                                                | 2026-08-31 | SQL 文件：`sql/knowledge.sql`                                                                                            |
| 任务 3 数据源              | 完成（common-security 修复 + Nacos 配置下发）                                               | 2026-08-31 | 数据源在 Nacos `knowledge-biz.yml`                                                                                       |
| 任务 4 验证提交            | 完成：启动验证通过（端口 4338、Nacos 注册成功）；已提交并推送 GitLab `develop` 分支         | 2026-08-31 | 日志：Started KnowledgeApplication（18:56:06）；commit b12b3ec，remote=gitlab.ahyzc.com/dbcenter/knowledge/knowledge.git |

## 6. 遗留与备注

- DP-01 / DP-03 等评审项冻结，B07 / B08 阶段再裁定。
- 建表策略（2026-08 确认）：按需建表，表只在其实现步骤到来时新增迁移文件；20.2 的 18 张一期表 + 4 张预留表的归属映射见任务 2 第 4 点，各步骤细化。
- 前端 `knowledge-web` 已切换分支 `develop-cxxl`，正常。
- worker 模块已补齐（biz pom worker 依赖 2026-08-31 加回）；子包 pipeline / parser / chunking / embedding / indexing / evaluation 留到阶段 2 写 worker 代码时再建。
- 已推送 GitLab `develop` 分支（2026-08-31，commit b12b3ec）；docs/ 随代码一并提交（.gitignore 中 docs/ 忽略行已移除）。
- 下一步预告：阶段 1「核心领域模型 + 知识库管理闭环」（设计文档第 7 章；本期建表 kb_knowledge_base、kb_audit_log）。

## 7. 封档记录

- 封档日期：2026-08-31
- 结论：四条验收标准全部达成，阶段 0 目标（四模块可运行基线 + 数据库 + SQL 统一惯例 + 数据源接通 + 入库）完成。
- 移交后续步骤的事项：
  1. 首验项 A / B（租户拦截器注入、分页 total）——在 step-01 任务 4 验证（见 step-01 风险预案）；
  2. D1-8 通用字段（user_id / tenant_id）——待用户向他人确认（step-01 D1-8 暂停状态）；
  3. 知识库名称唯一性——待议（实现文档 I-8）；
  4. worker 子包分层——随阶段 2 建立。
