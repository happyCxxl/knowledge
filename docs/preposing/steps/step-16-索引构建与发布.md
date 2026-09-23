# 阶段 16：索引构建与发布（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                         |
|----------|--------------------------------------------------------------|
| 阶段     | 16（索引构建与发布：组合模型 + 追加路径 + 对账 + 发布/回退） |
| 状态     | 已完成（待提交）                                             |
| 方案依据 | 实施手册 §3 阶段 16（复用 knowledge-vector）                 |

## 1. 目标与范围

**目标**：按 2026-09 定稿的策略集合模型落地索引构建与发布闭环——一个组合 = 一个 Milvus 集合（`kb_{kbId}_{versionNo}`，集合只增不改），发布/回退 = MySQL 指针切换；文件产物就绪回调按组合追加（文件即版本），构建任务承担「注册组合 + 全量对账 + 发布判定」。

1. **组合模型（common + worker）**：
   - `KbIndexSet`/`KbIndexVersion` 实体（机器表口径：append-only、仅 create_time）+ `enums/index/`（触发类型/形态/状态机）+ `dto/response/index/` 五个 VO；
   - worker `BuildOrder`（构建命令，随 BUILD_INDEX 任务快照下发）+ `ComboSnapshot`（组合身份 = 文件范围 × 索引形态 × 环节策略映射，JSON 记入 `combo_snapshot`）；
2. **构建任务链（biz）**：`IndexBuildTaskRunner`（领任务 → CREATED→BUILDING → 组合完整性/维度/血缘校验 → 集合生命周期 → LIST 冻结集回填 → 全量对账 → READY + 活账本统计 → 发布判定）、`IndexComboService`（绑定开 = KB 绑定策略集合单组合；绑定关 = 自动枚举产物完整组合）、`IndexComboReconciler`（对账单一事实源：期望 chunkId 集以校验时刻实时重算）、`IndexLineageResolver`（血缘解析：环节策略沿产物的 upstream 链读取，capabilitySnapshot 为载体，不新增列）、`IndexRowAssembler`（单文件装配索引行，追加路径与批量构建共用）；
3. **控制面（biz）**：`IndexSetService` + `KnowledgeFileIndexController`（7 接口：版本列表/组合枚举/候选构建/详情/验证/发布/回退/回收，挂 `/knowledge-base/{id}`）、`KbIndexSet/KbIndexVersionDbService(+Impl)+Mapper`；
4. **追加路径**：`EmbedTaskRunner` 成功后回调 `IndexSetService.onFileProductsReady(fileResultId)`（血缘解析 → 定位/复用组合集合 → upsert 幂等追加 → 对账 → READY → 绑定开且产物组合==绑定组合自动发布；产物组合 ≠ 在线组合按在线组合拓扑重放补齐）；
5. **契约**：`ErrorCode` 40441–40449 八码（40445 未落库：一致性校验失败经 validate VO 的 CONSISTENCY 子项呈现，无同步抛出路径）；
6. **SQL**：`kb_index_set` + `kb_index_version` 两表；`kb_file_result.owner`（检索强制过滤口径，一期统一 ADMIN）。

**不做什么**：检索与评测（阶段 17）；前端（阶段 18）；对外契约（阶段 19）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                                                          | 依据               |
|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| D16-1 | 一个组合 = 一个 Milvus 集合（`kb_{kbId}_{versionNo}`），集合只增不改；发布/回退 = 单事务三级指针切换（版本 ONLINE+publishedAt/By → 集合二级指针 → 知识库一级指针）            | 策略集合模型定稿   |
| D16-2 | 构建任务语义 = 注册组合 + 全量对账（实时重算期望 chunkId 集，吸收并发追加）+ 发布判定（COMPENSATE 恒自动 / INCREMENT 且绑定开自动 / LIST 永不自动）                           | step-13 B08 口径   |
| D16-3 | 追加路径由 EMBED 任务成功回调驱动（onFileProductsReady）；文件永不丢：产物组合 ≠ 在线组合时按在线组合拓扑重放补齐该文件                                                       | step-13 B08 口径   |
| D16-4 | 血缘解析沿产物的 upstream 链读取环节策略（capabilitySnapshot 为载体，不新增列）；组合映射按环节可扩展，ComboSnapshot 结构与构建逻辑零改动                                     | step-13 B1 口径    |
| D16-5 | 对账单一事实源：IndexComboReconciler 供构建任务全量对账与 validate API 共用                                                                                                   | step-13 B6 口径    |
| D16-6 | 机器表（kb_index_set/kb_index_version）append-only、仅 create_time、不挂平台五件套（与 kb_pipeline_task 同款）                                                                | step-13 B08 口径   |
| D16-7 | 组合枚举取数目录收口 `IndexComboReconciler.catalog`（枚举/列表弹窗共用批次口径，去重代码）；接口只保留生产调用的成员（currentPublished/listCombos 单参/枚举类方法留在实现类） | 0-warning 检测收敛 |

## 3. 任务清单

1. common：`KbIndexSet`/`KbIndexVersion` 实体 + `enums/index/`（IndexBuildTrigger/IndexShape/IndexVersionStatus）+ `dto/response/index/`（IndexVersionVO/IndexComboVO/IndexValidateVO/IndexValidateItemVO/IndexBuildTriggerVO）；
2. worker：`BuildOrder` + `ComboSnapshot`（+ `ComboSnapshotTest`）；
3. biz 控制面：`IndexSetService(+Impl)`、`KnowledgeFileIndexController`（7 接口）、`IndexComboService(+Impl)`、`IndexComboReconciler`、`IndexLineageResolver`、`IndexRowAssembler`、`IndexBuildTaskRunner`、`KbIndexSet/KbIndexVersionDbService(+Impl)+Mapper`（InfraDbService 收口）；
4. 追加路径：`EmbedTaskRunner` 成功回调 `onFileProductsReady` + `IndexSetServiceImpl` 追加路径实现；
5. 契约：`ErrorCode` 40441–40449 八码（40445 未落库：一致性校验失败经 validate VO 呈现）；
6. SQL：`stage-16-索引构建与发布.sql`（两表 + kb_file_result.owner ALTER）；
7. 单测：`IndexSetServiceImplTest`、`IndexComboServiceImplTest`、`IndexBuildTaskRunnerTest`、`IndexComboReconcilerTest`、`KbIndexSetDbServiceImplTest`、`KbIndexVersionDbServiceImplTest`、`ComboSnapshotTest` + `EmbedTaskRunnerTest` 回调断言更新；
8. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（526 例：common 4 + infra 5 + auth 8 + model 3 + vector 8 + worker 233 + biz 265）
- [x] 隔离验证：后续阶段文件移出 → 全绿 → 恢复零丢失
- [x] SQL 迁移执行：kb_index_set/kb_index_version 建表 + kb_file_result.owner 列（docker 已验证）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调 + Milvus 运行）：候选构建 → 对账 → 发布 → 检索

## 5. 执行记录

| 任务         | 执行结果 | 备注                                                                                   |
|--------------|----------|----------------------------------------------------------------------------------------|
| 1 common     | 完成     | 2 实体 + 3 枚举 + 5 VO                                                                 |
| 2 worker     | 完成     | BuildOrder/ComboSnapshot + 快照全等/序列化单测                                         |
| 3 biz 控制面 | 完成     | 控制面 7 接口 + 组合口径 + 对账 + 血缘 + 装配 + 任务执行器 + DB 层 InfraDbService 收口 |
| 4 追加路径   | 完成     | EmbedTaskRunner 成功回调 onFileProductsReady + 单测断言                                |
| 5 契约       | 完成     | 40441–40449 八码（40445 未落码：一致性失败经 validate VO 呈现）                        |
| 6 SQL        | 完成     | 两表 + owner 列已执行（docker 验证表结构与列）                                         |
| 7 单测       | 完成     | 526 例全绿（较阶段 15 的 451 例新增 75 例）                                            |
| 8 文档       | 完成     | 本档案                                                                                 |
