# 阶段 9：切片策略（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                                  |
|----------|-----------------------------------------------------------------------|
| 阶段     | 9（切片策略 / CHUNK）                                                 |
| 状态     | 已完成（待用户提交）                                                  |
| 方案依据 | 原项目档案（stash：step-07-B05切片策略.md + step-08-切片策略扩展.md） |

## 1. 目标与范围

**目标**：落地 CHUNK 环节——把预处理视图（检索文本口径）按"结构优先 + 招投标行规"切成适合检索的片段集合（ChunkSet）：四路内容路由、表头随片、父子层级、titlePath/sourceElementIds 全链溯源，为向量化提供唯一依赖的稳定输入。

1. CHUNK 独立任务：手动逐环节触发（`POST /file-results/{id}/chunk`，strategyVersionId/upstreamProductId 可选）+ 切片详情；
2. 四路切片：正文（5 算法：段落聚合/标题边界/结构混合/句聚合/固定窗口）、表格（4 算法：行级/行组/整表/上下文合并，Markdown 表格格式）、图片（图注占位）、兜底（递归/固定窗口/不兜底）；
3. 父子层级：章节父片 + 子片 parentChunkId（父片向量化归阶段 13 开关）；
4. 切片补充：titlePath（≤3 级）、sourceElementIds、pageRange/tableRef/order、charCount/tokenCount；
5. 策略版本：复用 `kb_pipeline_strategy_version`（type=CHUNK，结构化快照 routes+pipeline，5 条 seed）；解析三档 + 快照进 `task.strategy_snapshot`；
6. 产物落库：ChunkSet 写产物存储 + CHUNK product 行（upstream=PREPROCESS 产物）+ `kb_chunk_set` + `kb_chunk` 分批插入（≤500/批）+ 6 条子步骤记录。

**不做什么**：语义类切片（QA 分段/Contextual Retrieval，需向量化与评测基线）；触发向量化（阶段 13）；图片内文字识别（OCR 预留）；策略管理 CRUD 与保存校验（阶段 11）。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                                                                                                                                           | 依据           |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| D9-1 | 阶段合并：worker.chunking 为"B05 切片 + 切片扩展（算法菜单/参数化）"合并最终态（结构化策略模型 routes+pipeline、四路多算法、管线层参数），本阶段整包落地；原阶段 10 仅余策略保存校验（随阶段 11）与前端（随阶段 18）           | 用户拍板       |
| D9-2 | 手动逐环节：CHUNK 由页面手动触发/重跑同入口；PREPROCESS 成功后停在终态、不自动登记（延续阶段 6/7/8 口径）                                                                                                                      | R7-1           |
| D9-3 | 触发语义：无 PREPROCESS 产物 → 40432 + "预处理视图产物不存在，请先触发预处理"；RUNNING → 40431；QUEUED → 补投唤醒；终态/无 → 新建任务（upstream + 策略快照）入队                                                               | R7-1 最终态    |
| D9-4 | 策略解析三档：显式 strategyVersionId（40433 校验存在/类型/启用）> 启用中最新 > 内置默认 chunk-hybrid-v1；KB 绑定档剥离留阶段 12；40433 提示语通用化                                                                            | R7-2           |
| D9-5 | content 口径：normalizedText；剔除态与重复份跳过（PreprocessViewRules.CHUNK_SKIP_STATUSES 收口）；仅标记态参与；表格片为合法 Markdown 表格                                                                                     | R7-6 最终态    |
| D9-6 | 失败口径：上游产物缺失/反序列化失败 → FAILED(CHUNK_EMPTY)；执行异常 → CHUNK_FAILED；单切片器异常隔离 → PARTIAL_SUCCESS                                                                                                         | R7-8           |
| D9-7 | 详情内联 + 契约复用：ChunkDetailVO 实现 TaskStatusView（taskStatus→status）、子步骤 StepLogVO.of/copyFrom；触发走 TaskTriggerSupport、详情任务解析走 TaskDetailSupport、产物落库走 ProductPersistence；切片列表数据源 kb_chunk | D6-5/D7-6 延续 |

## 3. 任务清单

1. common：`domain.chunk` 3 值对象 + `enums.chunk` 5 枚举 + `ChunkRules` + `KbChunkSet/KbChunk` 实体 + `dto.response.chunk` 4 个 VO（Detail 改造）核对保留；PipelineTaskErrorCode + CHUNK_EMPTY/CHUNK_FAILED；ErrorCode 40433 提示语通用化；
2. worker：`chunking` 包整包（根主干 + slice/ 契约 + strategy/ 策略模型 + impl/ 管线/注册表/五路切片器）+ 15 测试类（原文核对保留）；
3. biz 数据层：KbChunkSetDbService/KbChunkDbService(+Impl)（InfraDbService 模式收口；getByArtifactId/listByChunkSetId，其余查询随后续阶段补齐）；
4. biz 任务层：ChunkTaskRunner 重写（FileStorage 读 PREPROCESS 视图 + STRUCTURE 参照（缺失不阻断）→ ChunkerPort.chunk → ProductPersistence + 两表分批插入）；ParseTaskConsumer 扩四类 + chunkRunner 分支；
5. biz 控制面：ChunkControlServiceImpl 重写（CHUNK 策略三档 + 内联上游校验 + 详情走 kb_chunk 数据源）+ KnowledgeFileChunkController（R 导入修正）；
6. SQL：执行 stage-09-切片策略.sql（kb_chunk_set + kb_chunk 建表 + 5 条 CHUNK seed）；
7. 单测：ChunkTaskRunnerTest 9 例 + ChunkControlServiceImplTest 11 例重写（KB 绑定四例随阶段 12 顺延）；worker 15 测试 + ChunkVoAssemblerTest 保留；
8. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 175 + biz 115 = 307）
- [x] 隔离验证：154 个后续阶段文件移出 → 全绿 → 恢复零丢失
- [x] stage-09 SQL 已执行（kb_chunk_set/kb_chunk 建表 + 5 条 CHUNK seed 落库核验）
- [ ] 真实冒烟：上传文档 → 解析 → 组装 → 预处理 → 切片 → 切片详情（待联调）

## 5. 执行记录

| 任务              | 执行结果                                                                      | 备注                                               |
|-------------------|-------------------------------------------------------------------------------|----------------------------------------------------|
| 1 common 切片契约 | 完成：3 值对象 + 5 枚举 + 规则清单 + 2 实体 + 4 VO + 错误码                   | 原文核对保留；TaskStatusView 契约复用              |
| 2 worker 切片引擎 | 完成：chunking 整包 + 15 测试类                                               | 纯算法零适配层引用，原文保留                       |
| 3 biz 数据层      | 完成：两切片 DbService（InfraDbService 收口）                                 | getByArtifactId/listByChunkSetId；其余查询后续阶段 |
| 4 biz 任务层      | 完成：ChunkTaskRunner + 消费泛化四类                                          | 结构参照缺失不阻断；分批插入 500/批                |
| 5 biz 控制面      | 完成：ChunkControlServiceImpl + KnowledgeFileChunkController                  | 策略三档；详情走 kb_chunk 数据源                   |
| 6 SQL             | 完成：stage-09 已执行（kb_chunk_set + kb_chunk + 5 条 CHUNK seed）            | 表随阶段出现                                       |
| 7 单测            | 完成：ChunkTaskRunnerTest 9 + ChunkControlServiceImplTest 11（重写）          | KB 绑定四例随阶段 12 顺延                          |
| 8 隔离验证        | 完成：154 移出 → 307/307 全绿 → 恢复零丢失；修正三处滞留测试缺失的 apply 调用 | —                                                  |

## 6. 下一步预告

阶段 11：策略管理菜单与知识库绑定（策略 CRUD + 保存校验 + 知识库-策略绑定）。
