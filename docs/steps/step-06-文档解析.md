# 阶段 6：文档解析（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                     |
|----------|----------------------------------------------------------|
| 阶段     | 6（文档解析）                                            |
| 状态     | 已完成（待用户提交）                                     |
| 方案依据 | 原项目档案（stash：step-04 文档解析任务文档 + 实现文档） |

## 1. 目标与范围

**目标**：落地文档解析全链路——消费循环 + 触发控制面 + worker 原生解析管线 + 产物落库 + 解析详情。

1. 消费循环：BRPOP 阻塞叫醒 → 扫库领批（仅 PARSE）→ claim 防重 → 线程池执行 + 看门狗超时；
2. 触发控制面：手动逐环节口径，`POST /file-results/{id}/parse` 四分支（首次解析与失败重跑同一入口）；
3. worker 原生解析：两级路由（Tika 探测 + 格式路由）、PDF/Office 解析器、六信号判定、90% 成功占比门槛；
4. 产物落库：内容寻址产物（file-center）+ `kb_pipeline_product`（PARSE）+ `kb_pipeline_step_log`（子步骤）；
5. 解析详情：任务/子步骤/质量告警组装。

**不做什么**：STRUCTURE 自动触发（成功后停终态，阶段 7 才有触发接口）；运行记录 runs（阶段 14）；OCR 扫描件解析（信号告警预留）；组装/预处理/切片/向量化/索引各环节。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                                                                                                                                    | 依据      |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| D6-1 | 消费模型：BRPOP 阻塞叫醒（空队零开销）→ 被叫醒才扫库领批（`listQueuedByStage(PARSE)`）→ claim 条件更新防多实例重复；看门狗 `Future.get(timeout)` 超时协作中断并回写 FAILED(EXECUTOR_TIMEOUT)                            | step-04   |
| D6-2 | 触发四分支：RUNNING → 40431；QUEUED → 补投唤醒不新建任务；SUCCESS/PARTIAL_SUCCESS → 40437 禁止重跑；FAILED/CANCELLED/无 → 新建 QUEUED 任务入队（旧任务/旧产物保留，重跑 = 新任务历史可查）                              | 原口径    |
| D6-3 | 产物存储走 file-center：`FileStorage.putObject` 内容寻址（artifactId = sha256 = contentHash）；删除 worker adapter 端口层（FileManagerPort/ArtifactRepositoryPort），解析文件经 `FileStorage.open` 开流                 | D4 承接   |
| D6-4 | 子步骤落库收口：`StepLogPersistence` 统一（默认值补齐 attempt=1、统计字段 0、错误截断 1000），各环节 Runner 共用                                                                                                        | 收口口径  |
| D6-5 | 解析详情内联控制面：parseDetail 按 taskId（缺省取 PARSE 最新任务）组装；不引入 PipelineQuerySupport；任务摘要/子步骤映射收口为公共契约接口默认方法（TaskStatusView.applyFrom / StepLogFields.copyFrom），四环节详情复用 | 裁剪口径  |
| D6-6 | 单环节领批：消费循环只分发 PARSE；其余环节 Runner 落地即接入，主循环零改动                                                                                                                                              | 阶段 7 起 |

## 3. 任务清单

1. common：parse 域模型 27 文件 + parse 枚举 6 + RowStatus/StepStatus + ParseDetailVO/StepLogVO/StageTriggerVO + KbPipelineProduct/KbPipelineStepLog 实体 + TextUtil；
2. worker：parser 包 32 文件（两级路由、能力注册、六信号、兜底、PDF/Office 解析器）+ 5 测试类 25 例；
3. biz 消费链：ParseTaskConsumer + ParseTaskRunner + StepLogPersistence；TaskQueueProperties 补 concurrency/claimBatchSize；
4. biz 控制面：ParseControlService(+Impl) + KnowledgeFileParseController；ErrorCode 补 40437 PARSE_ALREADY_SUCCEEDED；
5. biz 数据层：product/stepLog mapper + DbService；KbPipelineTaskDbService 补 listQueuedByStage/claim/updateProductId；
6. SQL：stage-06（kb_pipeline_product + kb_pipeline_step_log）执行落库；
7. 单测：ParseTaskRunnerTest 6 例 + ParseControlServiceImplTest 9 例（FileStorage mock 替换端口 mock，runs 用例随阶段 14 顺延）；
8. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 38 + biz 61 = 116）
- [x] 隔离验证：378 个后续阶段文件移出 → 全绿 → 恢复零丢失
- [ ] 真实冒烟：上传 PDF → 触发解析 → 队列消费 → 产物落库（待联调）

## 5. 执行记录

| 任务              | 执行结果                                                                  | 备注                                                |
|-------------------|---------------------------------------------------------------------------|-----------------------------------------------------|
| 1 common 解析契约 | 完成：27 域模型 + 6 枚举 + 2 实体 + 3 VO + TextUtil                       | 与原档案逐文件核对保留                              |
| 2 worker 解析引擎 | 完成：parser 32 文件 + 5 测试类 25 例                                     | 纯算法零适配层引用，原文保留；fact 常量参数告警修复 |
| 3 biz 消费链      | 完成：ParseTaskConsumer + ParseTaskRunner + StepLogPersistence            | 看门狗超时 → EXECUTOR_TIMEOUT                       |
| 4 biz 控制面      | 完成：ParseControlService(+Impl) + KnowledgeFileParseController           | 四分支触发；详情内联 + 重复映射收口                 |
| 5 产物链路        | 完成：FileStorage.putObject/getObject 替换已删除的 ArtifactRepositoryPort | artifactId = sha256 = contentHash                   |
| 6 任务数据层      | 完成：listQueuedByStage/claim/updateProductId                             | claim 条件更新防重                                  |
| 7 SQL             | 完成：stage-06 已执行（kb_pipeline_product + kb_pipeline_step_log）       | 表随阶段出现                                        |
| 8 单测            | 完成：ParseTaskRunnerTest 6 + ParseControlServiceImplTest 9（重写）       | runs 用例随阶段 14 顺延                             |
| 9 隔离验证        | 完成：378 移出 → 116/116 全绿 → 恢复零丢失                                | git status 恢复基线 178 项                          |

## 6. 下一步预告

阶段 7：统一文档模型（STRUCTURE 触发接口 + 标准化 + 阅读顺序 + 结构组装 + 溯源）。
