# 阶段 13：向量化（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                      |
|----------|-------------------------------------------|
| 阶段     | 13（向量化 / EMBED）                      |
| 状态     | 已完成（待提交）                          |
| 方案依据 | 原项目档案（stash：step-11-B07向量化.md） |

## 1. 目标与范围

**目标**：落地 EMBED 环节——切片文本按绑定策略原样编码为向量本体（EmbeddingSet），含前置兼容校验、四关校验、账本复用、集合文件落盘、策略管理与知识库绑定、控制面/任务全套工程配套。

三条红线：① 不改内容（inputText=content 原样）② 不做兜底（不兼容前置报错 40435）③ 不碰向量库（写库归阶段 16）。

1. **独立模型能力模块 `knowledge-model`（本阶段新建，用户拍板）**：模型目录（ModelCatalogPort/StaticModelCatalog）+ 模型网关端口（ModelGatewayPort）+ 阿里云 DashScope 提供者（OpenAI 兼容协议、Bearer 鉴权、RestClient 调用，DashScopeProperties 配置）；供应商替换只需新增实现，调用方零改动；
2. worker 向量化包：EmbedderPort/EmbedContext/EmbedWindowRules + strategy（EmbedStrategy/EmbedAlgorithmSpec/EmbedStrategyParser）+ check（四关）/template（IdentityTemplate）+ impl（EmbedPipeline 七步/EmbedHashes/EmbedConsistencyChecker）；
3. 账本复用：同 fileResultId + 同策略历史集合回溯（新→旧、miss 清零早退、上限 10）；cacheEnabled=OFF 强制重算；
4. biz：EmbedControlService/Impl（四档策略解析 + 窗口前置校验 40435）/EmbedController（R 导入修正）/EmbedTaskRunner（FileStorage + ProductPersistence + TaskRunnerSupport 收口）/EmbedVoAssembler/两表 DbService（InfraDbService 模式收口）；消费循环 EMBED 分发（timeout 10m）；
5. 策略管理：SUPPORTED_TYPES/BINDABLE_TYPES 增 EMBED；保存校验 EMBED 分支（目录/模板占位符/数值范围/枚举 40001）+ enrich 目录冗余进快照；
6. KB 展示：KnowledgeBaseVO 增 embedStrategyVersion 摘要（填充泛化三类型）。

**不做什么**：前端（策略表单 EMBED tab、环节页、KB 表单下拉，随阶段 18）；写向量库与索引（阶段 16）；索引自动构建回调（阶段 16 接入）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                                    | 依据     |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| D13-1 | 新建独立模块 knowledge-model 提供模型能力（目录 + 网关端口 + 供应商实现），worker/biz 依赖之；删除参考实现的网关 Feign 适配（外部平台依赖）             | 用户拍板 |
| D13-2 | 一期供应商 = 阿里云 DashScope（OpenAI 兼容协议 /embeddings，Bearer 鉴权）；模型目录改为阿里云模型（text-embedding-v4 默认启用 1024 维；v3/v2 备选停用） | 用户拍板 |
| D13-3 | ModelGatewayPort 仅保留 embed 能力（OCR 预留不落端口，随图片 OCR 阶段再加）                                                                             | 项目范围 |
| D13-4 | EmbedTaskRunner 按本仓库产物模式重写（FileStorage + ProductPersistence + TaskRunnerSupport），索引自动构建回调推迟阶段 16                               | 项目约定 |
| D13-5 | EmbedControlServiceImpl 按控制面收口模式（TaskTriggerSupport/TaskDetailSupport/内联上游校验），不使用参考实现的 PipelineQuerySupport                    | 项目约定 |

## 3. 任务清单

1. 新建 knowledge-model 模块（pom + 5 主类 + 1 测试类）+ 根 pom 模块与依赖管理 + worker/biz pom 依赖；
2. common：EmbeddingModel 目录改阿里云模型；ErrorCode 40434/40435/40436；PipelineTaskErrorCode EMBED_EMPTY/EMBED_MODEL_INCOMPATIBLE/EMBED_CONSISTENCY_FAILED/EMBED_FAILED；
3. worker：embedding 包整包纳入（import 迁移 model 包）；删除 adapter/gateway 与 adapter/catalog（迁移至 knowledge-model）；
4. biz：EmbedTaskRunner 重写 + EmbedControlServiceImpl 重写 + EmbedVoAssembler/两表 DbService（InfraDbService 收口）+ 控制器 R 修正 + 消费循环 EMBED 分发 + StrategyVersionService/Impl 扩展 + KnowledgeBaseVO/Impl 三类型摘要；
5. SQL：stage-13-向量化.sql 执行（两表建表 + EMBED seed 模型改 text-embedding-v4）；
6. 单测：knowledge-model 提供者 3 例 + worker embedding 32 例（模型名改阿里云口径）+ biz 26 例（runner 7 + 控制面 10 + 策略校验 5 + 组装器 4）；
7. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + model 3 + worker 216 + biz 186 = 422）
- [x] 隔离验证：后续阶段文件移出（89）→ 全绿 → 恢复零丢失
- [x] stage-13 SQL 已执行（kb_embedding_set/kb_embedding_record 建表 + EMBED seed text-embedding-v4 核验）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调 + 阿里云 API Key 配置）：KB 绑定 EMBED → 触发向量化 → 换策略重跑看复用命中 → 四关拦截演练

## 5. 执行记录

| 任务              | 执行结果 | 备注                                                                                                                                            |
|-------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 knowledge-model | 完成     | 新模块 5 主类 + DashScopeEmbeddingProviderTest 3 例（修 2 处测试问题：MockRestServiceServer 绑 Builder、MockClientHttpRequest.getBodyAsString） |
| 2 common          | 完成     | 目录改阿里云三模型 + 6 错误码                                                                                                                   |
| 3 worker          | 完成     | embedding 包纳入 + import 迁移 + 网关措辞改模型口径                                                                                             |
| 4 biz             | 完成     | runner/控制面重写 + 收口 + 消费循环分发                                                                                                         |
| 5 SQL             | 完成     | docker 执行并核验（两表 + 2 seed text-embedding-v4）                                                                                            |
| 6 单测            | 完成     | 修 4 处测试问题（旧模型名改阿里云口径、32000→8192、两 biz 测试按新架构重写）                                                                    |
| 7 文档            | 完成     | 本档案                                                                                                                                          |
