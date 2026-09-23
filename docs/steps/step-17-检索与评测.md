# 阶段 17：检索与评测（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                        |
|----------|-------------------------------------------------------------|
| 阶段     | 17（检索与评测：双通道 + RRF 融合 + 测试台对比 + 选优发布） |
| 状态     | 已完成（待提交）                                            |
| 方案依据 | 实施手册 §3 阶段 17（参考 step-14 检索与评测）              |

## 1. 目标与范围

**目标**：按 B09/B10 规格落地检索与评测闭环——检索 = 规则解析 → 双通道召回（全文 BM25 / 向量 HNSW）→ RRF 融合 → 父片展开 → Top-K → 溯源返回；评测 = 同一执行引擎 + 显式 (versionId, ruleId) + 执行时刻快照对比 + 选优发布默认规则。

1. **检索策略模型（B1）**：worker `RetrievalRuleSpec`（全目录快照：channel/fusion/preprocess/rerank/postprocess/topK/scoreThreshold，预留项锁定 NONE/0）+ `RetrievalCapability`（一期全关的预留能力清单）+ biz `RetrievalCapabilityRegistry`（Nacos 开关 `retrieval.capabilities.enabled`）+ `RetrievalRuleResolver`（字段/取值白名单 + 参数边界 + 预留锁定 40451）；RETRIEVAL 进 `StrategyVersionService.SUPPORTED_TYPES`（规则注册复用策略版本机制，B0 不可变行口径天然适用）；
2. **绑定与发布（B2）**：`kb_index_version.default_rule_id`（阶段 16 DDL 已建）+ 回退链三级解析（在线版本行 → kb 默认 → 引擎基线常量）+ `RetrievalRuleService` 选优发布（切指针 + 审计 `RETRIEVAL_RULE_PUBLISH`）；
3. **查询向量化（B3）**：跟随组合快照 `stageStrategies[EMBED]` 的模型与 queryTemplate（`{query}` 占位替换），经 `ModelGatewayPort.embed`；纯全文规则跳过；网关失败该通道降级为空；
4. **检索引擎（B4）**：biz `SearchService(+Impl)` 唯一执行路径（生产回退链 / 测试台显式指定同一引擎）+ worker `RrfFusion` 纯函数（RRF 跨通道贡献求和）+ 父片展开（`MilvusIndexPort.queryChunkByIds` 集合内取父片）+ 预留能力引擎拒执行（双保险）；
5. **API 与留痕（B5/B6）**：`RetrievalController`（生产检索/测试台检索/运行记录列表/勾选对比回放/选优发布）+ retrieval 请求响应 DTO + `kb_retrieval_run` 机器表（测试台必记、生产按开关 `retrieval.run.record-production` 默认关；快照即证据，对比回放不重跑）；
6. **契约**：`ErrorCode` 40450/40451；`AuditActionType.RETRIEVAL_RULE_PUBLISH`；
7. **SQL**：`stage-17-检索与评测.sql`（kb_retrieval_run 表 + 预置 4 条 RETRIEVAL 规则）。

**不做什么**：前端检索入口/测试台视图（阶段 18）；对外契约（阶段 19）；预留能力解锁（只改 Nacos 开关）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                 | 依据         |
|-------|--------------------------------------------------------------------------------------------------------------------------------------|--------------|
| D17-1 | 规则 = kb_pipeline_strategy_version 的 RETRIEVAL 行（不可变快照）；规则不进组合全等判定（换规则不重建集合）                          | B0 + B8 口径 |
| D17-2 | 能力分层解锁三层锁：前端置灰（阶段 18）/注册校验拒绝非默认值 40451/引擎拒执行（双保险）；解锁只改 Nacos 开关                         | B09 规格     |
| D17-3 | 回退链三级：在线版本行 default_rule_id → kb.defaultRuleId → 引擎基线（不落库常量，body 同 hybrid-rrf-k60-top10-parent）              | B09 规格     |
| D17-4 | 查询向量化不是规则项：跟随集合 EMBED 策略（模型/queryTemplate）；纯 FULLTEXT 跳过向量化；网关失败该通道降级为空（HYBRID 不整体失败） | 不变量 2     |
| D17-5 | 快照即证据：对比回放只回放 kb_retrieval_run.result_snapshot，不重跑（append-only 集合不可重放）；测试台必记、生产按开关默认关        | 不变量 4/E   |
| D17-6 | 生产与测试台同一执行引擎：唯一路径 search(kbId, query, ruleSpec, collectionName)；测试台只多一层显式 (versionId, ruleId)             | 不变量 5     |

## 3. 任务清单

1. common：`KbRetrievalRun` 实体 + retrieval 请求/响应 DTO（SearchRequest/SearchVO/SearchHitVO/RetrievalRulePublishDto+VO/RetrievalRunCompareDto/RetrievalRunVO）+ ErrorCode 40450/40451 + AuditActionType.RETRIEVAL_RULE_PUBLISH；
2. worker：`RetrievalRuleSpec` + `RetrievalCapability` + `RrfFusion`（+ RrfFusionTest）；
3. biz 检索规则：`RetrievalRuleResolver` + `RetrievalCapabilityRegistry` + `RetrievalRunSettings` + RETRIEVAL 进 SUPPORTED_TYPES + StrategyVersionServiceImpl 注册校验接入；
4. biz 引擎与控制面：`SearchService(+Impl)` + `RetrievalRuleService(+Impl)` + `RetrievalController`（5 接口）+ `KbRetrievalRunDbService(+Impl)+Mapper`（InfraDbService 收口）；
5. SQL：`stage-17-检索与评测.sql`（kb_retrieval_run 表 + 4 条预置规则）；
6. 单测：`RetrievalRuleResolverTest`、`SearchServiceImplTest`、`RetrievalRuleServiceImplTest`、`RrfFusionTest` + `StrategyVersionServiceImplTest` RETRIEVAL 注册校验用例；
7. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（555 例：common 4 + infra 5 + auth 8 + model 3 + vector 8 + worker 236 + biz 291）
- [x] 隔离验证：后续阶段文件移出（3）→ 全绿 → 恢复零丢失
- [x] SQL 迁移执行：kb_retrieval_run 建表 + 4 条预置规则（docker 已验证）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调 + Milvus 运行）：上传→建冻结集→测试台双轴对比→选优发布→生产检索

## 5. 执行记录

| 任务         | 执行结果 | 备注                                                                               |
|--------------|----------|------------------------------------------------------------------------------------|
| 1 common     | 完成     | 实体 + 7 DTO + 40450/40451 + RETRIEVAL_RULE_PUBLISH                                |
| 2 worker     | 完成     | RetrievalRuleSpec/RetrievalCapability/RrfFusion + 纯函数单测                       |
| 3 规则模型   | 完成     | 解析校验器 + 能力注册表 + 留痕开关 + RETRIEVAL 白名单与注册校验接入                |
| 4 引擎控制面 | 完成     | SearchService/RetrievalRuleService/RetrievalController + DB 层 InfraDbService 收口 |
| 5 SQL        | 完成     | kb_retrieval_run + 4 条规则已执行（docker 验证）                                   |
| 6 单测       | 完成     | 555 例全绿（较阶段 16 的 526 例新增 29 例）                                        |
| 7 文档       | 完成     | 本档案                                                                             |
