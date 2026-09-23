# 阶段 8：数据预处理（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                          |
|----------|-----------------------------------------------|
| 阶段     | 8（数据预处理 / PREPROCESS）                  |
| 状态     | 已完成（待用户提交）                          |
| 方案依据 | 原项目档案（stash：step-06-B04数据预处理.md） |

## 1. 目标与范围

**目标**：落地 PREPROCESS 环节——解析/组装完成"识别"（页眉页脚/目录/重复/噪声标记），预处理只做"处置"（按可配置策略开关打标记/剔除）+ 生成五层派生视图（rawText/displayText/normalizedText/normalizedFields/preprocessTrace），**原文与结构零改动**，为切片提供检索文本口径。

1. PREPROCESS 独立任务：手动逐环节触发（`POST /file-results/{id}/preprocess`，strategyVersionId/upstreamProductId 可选）+ 预处理详情；
2. 八步处置链（顺序固定、只开关）：编码规范化 → 文本整理 → 页眉页脚处置 → 目录处置 → 重复处置 → 字段规范化 → 噪声处置 → 自定义规则 + 视图组装（9 条子步骤记录）；
3. 策略版本：`kb_pipeline_strategy_version` 表 + 5 条 PREPROCESS seed；策略解析三档（显式指定 > 启用中最新 > 内置默认），快照进 `task.strategy_snapshot`（触发时固定）；
4. 产物落库：PreprocessView 写产物存储 + PREPROCESS product 行（upstream 链指向 STRUCTURE 产物）；
5. 消费循环泛化：PARSE + STRUCTURE + PREPROCESS 三类按 stage 分发。

**不做什么**：识别之外的模型增强（全部规则识别）；人工复核改写（"拿不准"只标 MANUAL_REVIEW）；触发切片（阶段 9）；策略版本管理接口与知识库-策略绑定（阶段 11/12）；分词与语义增强。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                                                                                                                                                                                                             | 依据           |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| D8-1 | 手动逐环节：PREPROCESS 由页面手动触发/重跑同入口；STRUCTURE 成功后停在终态、不自动登记（延续阶段 6/7 口径）                                                                                                                                                      | R6-1           |
| D8-2 | 触发语义：无 STRUCTURE 产物 → 40432 + "统一结构产物不存在，请先触发组装"；RUNNING → 40431；QUEUED → 补投唤醒；终态/无 → 新建任务（upstream + 策略快照）入队                                                                                                      | R6-1 最终态    |
| D8-3 | 策略解析三档：显式 strategyVersionId（40433 校验存在/类型/启用）> 启用中最新 > 内置默认 preproc-default-v1；KB 绑定档剥离留阶段 12                                                                                                                               | R6-2           |
| D8-4 | 产物链路：PreprocessView 走 FileStorage.putObject（artifactId = sha256 = contentHash）；product 行 upstream 指 STRUCTURE 产物行；能力快照=策略快照                                                                                                               | D6-3 延续      |
| D8-5 | 失败口径：上游产物缺失/反序列化失败 → FAILED(PREPROCESS_EMPTY)；执行异常 → PREPROCESS_FAILED；只建议 SUCCESS/FAILED（无 PARTIAL_SUCCESS 一期口径）                                                                                                               | R6-13          |
| D8-6 | 详情内联 + 契约复用：PreprocessDetailVO 实现 TaskStatusView（taskStatus→status）、子步骤 StepLogVO.of/copyFrom；触发走 TaskTriggerSupport（增 strategySnapshot 可选参数）、详情任务解析走 TaskDetailSupport、产物落库走 ProductPersistence；预处理详情纳入本阶段 | D6-5/D7-6 延续 |

## 3. 任务清单

1. common：`domain.preprocess` 6 值对象 + `enums.preprocess` 6 枚举 + `PreprocessViewRules` + `KbPipelineStrategyVersion` 实体 + `dto.response.preprocess` 7 个 VO（Detail 改造）核对保留；ErrorCode + 40433；PipelineTaskErrorCode + PREPROCESS_EMPTY/PREPROCESS_FAILED；
2. worker：`preprocessing` 包 27 文件（八规则 + 管线 + 策略模型）+ 12 测试类（原文核对保留）；
3. biz 数据层：KbPipelineStrategyVersionMapper/DbService(+Impl)（InfraDbService 模式收口，只保留 getLatestEnabledByType/getEnabledByTypeAndVersion）；
4. biz 任务层：TaskTriggerSupport.trigger 增 strategySnapshot 可选参数（parse/structure 零影响）；ParseTaskConsumer 扩三类 + preprocessRunner 分支；PreprocessTaskRunner 重写（FileStorage + ProductPersistence + 内联上游解析）；
5. biz 控制面：PreprocessControlServiceImpl 重写（策略三档 + 内联上游校验 + 详情）+ KnowledgeFilePreprocessController（R 导入修正）；
6. SQL：执行 stage-08-数据预处理.sql（kb_pipeline_strategy_version + 5 条 PREPROCESS seed）；
7. 单测：PreprocessTaskRunnerTest 4 例 + PreprocessControlServiceImplTest 11 例重写（KB 绑定三例随阶段 12 顺延）；PreprocessVoAssemblerTest 保留；
8. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 121 + biz 94 = 232）
- [x] 隔离验证：234 个后续阶段文件移出 → 全绿 → 恢复零丢失
- [x] stage-08 SQL 已执行（kb_pipeline_strategy_version + 5 条 seed 落库核验）
- [ ] 真实冒烟：上传 PDF → 解析 → 组装 → 预处理 → 五层视图落库（待联调）

## 5. 执行记录

| 任务                | 执行结果                                                                       | 备注                                  |
|---------------------|--------------------------------------------------------------------------------|---------------------------------------|
| 1 common 预处理契约 | 完成：6 值对象 + 6 枚举 + 规则清单 + 实体 + 7 VO + 错误码                      | 原文核对保留；TaskStatusView 契约复用 |
| 2 worker 预处理引擎 | 完成：preprocessing 27 文件 + 12 测试类                                        | 纯算法零适配层引用，原文保留          |
| 3 biz 数据层        | 完成：策略版本 DbService（InfraDbService 收口）                                | 只读两查询；管理方法留阶段 11         |
| 4 biz 任务层        | 完成：触发助手扩展 + 消费泛化 + PreprocessTaskRunner                           | 三类领批分发；策略快照触发时固定      |
| 5 biz 控制面        | 完成：PreprocessControlServiceImpl + KnowledgeFilePreprocessController         | 策略三档；详情内联 + 契约复用         |
| 6 SQL               | 完成：stage-08 已执行（kb_pipeline_strategy_version + 5 条 PREPROCESS seed）   | 表随阶段出现                          |
| 7 单测              | 完成：PreprocessTaskRunnerTest 4 + PreprocessControlServiceImplTest 11（重写） | KB 绑定三例随阶段 12 顺延             |
| 8 隔离验证          | 完成：234 移出 → 232/232 全绿 → 恢复零丢失                                     | —                                     |

## 6. 下一步预告

阶段 9：切片策略（CHUNK 独立任务手动触发；四路切片 + 父子层级 + 溯源；content=normalizedText 口径）。
