# 阶段 14：执行链工作台与发布（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                               |
|----------|----------------------------------------------------|
| 阶段     | 14（执行链工作台与策略集合发布 / 后端）            |
| 状态     | 已完成（待提交）                                   |
| 方案依据 | 原项目档案（stash：step-12-执行链工作台与发布.md） |

## 1. 目标与范围

**目标**：支撑评测阶段多分支执行链可见性——执行树聚合、产物内容对比、策略集合批量发布（后端三接口）。

1. **执行树聚合**：`GET /file-results/{id}/lineage` → nodes（taskId/stage/status/策略版本/能力快照/产物引用/统计摘要）+ edges（upstreamProductId 反查血缘）；数据源 = 任务表全环节 + product + 各环节摘要表；
2. **产物内容**：`GET /file-results/{id}/stage-content?stage=&taskId=`（白名单五环节，非法 40001）；按 task.productId 精确取该次运行产物（历史任务同样可展示）；对齐键：PARSE/STRUCTURE/PREPROCESS=elementId、CHUNK/EMBED=顺序号；
3. **策略集合批量发布**：`PUT /knowledge-base/{id}/strategy-bindings` 一次调用设置整套（type 白名单/不重复/版本校验/空版本解绑/未提及类型不动/逐类型审计 BIND，事务覆盖）。

**不做什么**：前端（执行链工作台链图/文件面板/对比视图/设为知识库策略交互，随阶段 18）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                               | 依据         |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| D14-1 | 发布语义 = 知识库策略集合（复用 kb_strategy_binding，不建新表、不指向产物）；批量接口一次调用设置整套                                              | 参考拍板口径 |
| D14-2 | StageContentQueryServiceImpl 按本仓库产物模式适配（FileStorage + TaskDetailSupport），不使用参考实现的 PipelineQuerySupport/ArtifactRepositoryPort | 项目约定     |
| D14-3 | 数据层补 listByFileResultId（KbPipelineTask/KbChunkSet，执行树聚合数据源）                                                                         | B1 需要      |

## 3. 任务清单

1. common：LineageVO/LineageNodeVO/LineageEdgeVO + StageContentVO/StageContentItemVO + StrategyBindingsUpdateRequest 纳入；
2. biz 接口：LineageController/LineageQueryService(+Impl) + StageContentController/StageContentQueryService(+Impl，FileStorage 适配)；
3. biz 批量绑定：KnowledgeBaseService/Impl bindStrategies + Controller PUT /strategy-bindings；
4. 数据层：KbPipelineTaskDbService/KbChunkSetDbService 增 listByFileResultId(+Impl)；
5. 单测：LineageQueryServiceImplTest 4 例 + StageContentQueryServiceImplTest 8 例（构造器适配）+ KnowledgeBaseServiceImplTest 批量绑定 5 例；
6. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + model 3 + worker 216 + biz 198 = 434）
- [x] 隔离验证：后续阶段文件移出（75）→ 全绿 → 恢复零丢失
- [x] 无新 SQL（复用 kb_strategy_binding）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调）：多分支执行链血缘/内容对比/批量设为知识库策略

## 5. 执行记录

| 任务       | 执行结果 | 备注                                                                                                    |
|------------|----------|---------------------------------------------------------------------------------------------------------|
| 1 common   | 完成     | 三组 VO + 批量绑定请求纳入                                                                              |
| 2 biz 接口 | 完成     | 三接口 + StageContent FileStorage 适配 + R 导入修正                                                     |
| 3 批量绑定 | 完成     | bindStrategies 复用单类型逻辑 + 白名单/去重/事务                                                        |
| 4 数据层   | 完成     | 两处 listByFileResultId 补齐                                                                            |
| 5 单测     | 完成     | 17 例（lineage 4 + stage-content 8 + 批量绑定 5）；修 3 处编译问题（R 导入、List 导入、缺失数据层方法） |
| 6 文档     | 完成     | 本档案                                                                                                  |
