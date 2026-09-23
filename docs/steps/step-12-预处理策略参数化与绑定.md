# 阶段 12：预处理策略参数化与绑定（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                   |
|----------|--------------------------------------------------------|
| 阶段     | 12（预处理策略参数化与绑定 / 收尾接线）                |
| 状态     | 已完成（待用户提交）                                   |
| 方案依据 | 原项目档案（stash：step-10-预处理策略参数化与绑定.md） |

## 1. 目标与范围

**目标**：预处理策略真正决定预处理行为（规则分组 + 规则级参数 + 自定义正则），并接入知识库-策略绑定（预处理触发链 KB 绑定优先）。

主体机制（结构化策略模型 rules/custom、PreprocessAlgorithmSpec 解析/补默认/校验、8 规则参数化、自定义正则执行器、结构化 seed）已在阶段 8 落地；本阶段收尾 biz 侧接线：

1. 策略类型扩展：策略管理 SUPPORTED_TYPES / BINDABLE_TYPES 增 PREPROCESS；保存校验增 PREPROCESS 分支（结构/规则键白名单/action 与 enabled 枚举/参数范围/自定义正则 ≤20 条，非法 40001）；
2. 触发链绑定档：预处理触发解析升级四档（显式 > KB 绑定 > 全局最新启用 > 内置默认，失效回退告警）；
3. KB 展示：详情/列表填充 preprocessStrategyVersionId/preprocessStrategyVersion 摘要（填充逻辑泛化 PREPROCESS/CHUNK 两类型）。

**不做什么**：前端（策略表单 PREPROCESS 分支、KB 表单预处理下拉、卡片两行绑定，随阶段 18）；SQL（seed 已结构化且库内已核验）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                      | 依据     |
|-------|-----------------------------------------------------------------------------------------------------------|----------|
| D12-1 | 主体机制已随阶段 8 落地（审计与参考项目逐文件核对一致，仅 Java 21 语法现代化差异），本阶段仅 biz 收尾接线 | 审计结论 |
| D12-2 | 绑定摘要填充泛化为 PREPROCESS/CHUNK 两类型（按类型分发 VO 字段），EMBED 随阶段 13 加入                    | D11 延续 |
| D12-3 | 无新 SQL：preproc 5 条 seed 已结构化（rules/custom），库内核验一致                                        | 审计结论 |

## 3. 任务清单

1. biz 策略类型：StrategyVersionService SUPPORTED_TYPES/BINDABLE_TYPES 增 PREPROCESS；StrategyVersionServiceImpl.validateConfig 增 PREPROCESS 分支（PreprocessAlgorithmSpec.validate）；
2. biz 触发链：PreprocessControlServiceImpl 插 KB 绑定档位（四档解析 + 失效回退告警，构造器增绑定/知识库两依赖）；
3. biz 展示：KnowledgeBaseVO 增预处理绑定摘要两字段；KnowledgeBaseServiceImpl 填充逻辑泛化两类型；
4. 单测：StrategyVersionServiceImplTest 增 PREPROCESS 校验 5 例；PreprocessControlServiceImplTest 构造器适配 + 绑定 3 例；KnowledgeBaseServiceImplTest 增预处理摘要用例；
5. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 184 + biz 160 = 361）
- [x] 隔离验证：后续阶段文件移出 → 全绿 → 恢复零丢失
- [x] 无新 SQL（preproc seed 库内核验已结构化）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调）：同文件多套预处理策略对比（页眉页脚 EXCLUDE vs KEEP、toc 阈值、自定义规则清特定水印）；KB 绑定预处理策略触发

## 5. 执行记录

| 任务           | 执行结果 | 备注                                                                                                                        |
|----------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| 1 策略类型扩展 | 完成     | SUPPORTED_TYPES/BINDABLE_TYPES + PREPROCESS 校验分支                                                                        |
| 2 触发链       | 完成     | 四档解析 + 失效回退告警                                                                                                     |
| 3 KB 展示      | 完成     | VO 两字段 + 填充泛化两类型                                                                                                  |
| 4 单测         | 完成     | 新增 9 例（校验 5 + 绑定 3 + 摘要 1）；修 1 处：listWithUnknownType 用例的"未知类型"原用 PREPROCESS，本阶段合法化后改 INDEX |
| 5 文档         | 完成     | 本档案                                                                                                                      |
