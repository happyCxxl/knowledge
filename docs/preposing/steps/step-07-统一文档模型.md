# 阶段 7：统一文档模型（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                            |
|----------|-------------------------------------------------|
| 阶段     | 7（统一文档模型 / STRUCTURE）                   |
| 状态     | 已完成（待用户提交）                            |
| 方案依据 | 原项目档案（stash：step-05-B03统一文档模型.md） |

## 1. 目标与范围

**目标**：落地 STRUCTURE 环节——把多路解析结果封装焊接成知识库内部唯一稳定的 UnifiedDocument（schemaVersion=1.0.0），保留每个元素回到原文的位置信息。

1. STRUCTURE 独立任务：手动逐环节触发（`POST /file-results/{id}/structure`，首次触发与重跑同一入口）+ 组装详情；
2. 组装工艺七接口：元素标准化、去重合并（IoU + 文本相似双判定）、XY-cut 阅读顺序、结构组装（标题规则链 + 章节树）、续表接续、模型兜底扩展点骨架、重复噪声识别；
3. 溯源校验：逐元素可回原文，AssembleReport 统计（可回溯占比）；
4. 产出落库：UnifiedDocument 写产物存储 + STRUCTURE product 行（upstream 链指向 PARSE 产物）+ 5 条子步骤记录；
5. 消费循环泛化：PARSE + STRUCTURE 两类任务按 stage 分发。

**不做什么**：结构修正接口与人工确认；模型判断实现（仅接口骨架与开关，一期走固定规则降级）；触发预处理（阶段 8）；OCR/版面/表格模型实现。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                                                                                                                                                                                                                                       | 依据        |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|
| D7-1 | 手动逐环节：STRUCTURE 由页面手动触发/重跑同一入口；PARSE 成功后停在终态、不自动登记组装任务（延续阶段 6 口径）                                                                                                                                                                                                             | R5-1 修订   |
| D7-2 | 触发语义：RUNNING → 40431；QUEUED → 补投唤醒；SUCCESS/FAILED/无 → 新建任务（成功后允许重跑，与解析 40437 不同）；无解析产物 → 40432 + 消息                                                                                                                                                                                 | R5-3 最终态 |
| D7-3 | 消费泛化：`listQueuedByStages(PARSE+STRUCTURE)` 按 stage 分发（兑现 D6-6 零改动接入）；看门狗 `timeoutOf(STRUCTURE)`=5m（配置已备）                                                                                                                                                                                        | R5-2        |
| D7-4 | 产物链路：UnifiedDocument 走 `FileStorage.putObject`（artifactId = sha256 = contentHash）；product 行 `upstream_product_id` 指 PARSE 产物行；能力快照 `{"schemaVersion":"1.0.0"}`；产物落库收口 ProductPersistence（各环节执行器共用：序列化 → 写产物存储 → 产物行 → 回写任务产物引用）                                    | D6-3 延续   |
| D7-5 | 失败口径：空树/上游产物缺失/反序列化失败 → FAILED(STRUCTURE_EMPTY)；执行异常 → STRUCTURE_FAILED；只建议 SUCCESS/FAILED（无 PARTIAL_SUCCESS）                                                                                                                                                                               | R5-5 最终态 |
| D7-6 | 详情内联 + 契约复用：StructureDetailVO 实现 TaskStatusView（taskStatus→status）、子步骤走 StepLogVO.of/copyFrom；触发防重/唤醒/建任务收口 TaskTriggerSupport、详情任务解析收口 TaskDetailSupport（解析/组装共用，未来环节复用）；删除 PipelineQuerySupport(+Test) 与 Artifact（产物存取统一走 FileStorage 门面，用户拍板） | D6-5 延续   |

## 3. 任务清单

1. common：structure 域模型 16 + 枚举 6 + 详情 VO 5（StructureDetailVO 改造）核对保留；PipelineTaskErrorCode + STRUCTURE_EMPTY/STRUCTURE_FAILED；
2. worker：structure 包 33 文件（七工艺 + 标题规则链六规则）+ 7 测试类 23 例（原文核对保留）；
3. biz 数据层：KbPipelineTaskDbService(+Impl) + listQueuedByStages；
4. biz 消费链：ParseTaskConsumer 泛化（PARSE+STRUCTURE 领批、按环节看门狗、STRUCTURE 分发 StructureTaskRunner）；
5. biz 执行器：StructureTaskRunner 重写（FileStorage 化 + 内联上游解析 + requireNonNull 防 NPE）；
6. biz 控制面：StructureControlServiceImpl 重写（内联文件校验/上游产物校验/任务解析/详情）+ KnowledgeFileStructureController（R 导入修正）；
7. 删除：PipelineQuerySupport(+Test)、Artifact；
8. 单测：StructureTaskRunnerTest 3 例 + StructureControlServiceImplTest 9 例重写；StructureVoAssemblerTest 保留；
9. SQL：无新增迁移脚本（upstream_product_id 列阶段 4/6 已备；kb_pipeline_product 无唯一约束冲突）；
10. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 61 + biz 77 = 155）
- [x] 隔离验证：301 个后续阶段文件移出 → 全绿 → 恢复零丢失
- [ ] 真实冒烟：上传 PDF → 解析 → 触发组装 → UnifiedDocument 落库（待联调）

## 5. 执行记录

| 任务              | 执行结果                                                                    | 备注                                                                      |
|-------------------|-----------------------------------------------------------------------------|---------------------------------------------------------------------------|
| 1 common 结构契约 | 完成：16 域模型 + 6 枚举 + 5 详情 VO（StructureDetailVO 改造）+ 错误码 2    | 原文核对保留；TaskStatusView 契约复用                                     |
| 2 worker 组装引擎 | 完成：structure 33 文件 + 7 测试类 23 例                                    | 纯算法零适配层引用，原文保留                                              |
| 3 biz 数据层      | 完成：listQueuedByStages                                                    | QUEUED + stage IN + id 升序限量                                           |
| 4 biz 消费链      | 完成：ParseTaskConsumer 泛化                                                | PARSE/STRUCTURE 领批、按环节看门狗                                        |
| 5 biz 执行器      | 完成：StructureTaskRunner                                                   | FileStorage 化 + 内联上游解析                                             |
| 6 biz 控制面      | 完成：StructureControlServiceImpl + KnowledgeFileStructureController        | 内联校验/详情；触发与任务解析收口共用助手；解析详情 taskId 校验对齐 40001 |
| 7 删除            | 完成：PipelineQuerySupport(+Test)、Artifact                                 | 用户拍板；未来环节同口径                                                  |
| 8 单测            | 完成：StructureTaskRunnerTest 3 + StructureControlServiceImplTest 9（重写） | FileStorage mock 替换端口 mock                                            |
| 9 SQL             | 无新增迁移脚本                                                              | 列与约束已在阶段 4/6 就位                                                 |
| 10 隔离验证       | 完成：301 移出 → 155/155 全绿 → 恢复零丢失                                  | —                                                                         |

## 6. 下一步预告

阶段 8：数据预处理（PREPROCESS 环节 + 策略版本表 + 派生视图 rawText/displayText/normalizedText + 页眉页脚处置策略）。
