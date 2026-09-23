# 阶段 11：策略管理菜单与知识库绑定（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                     |
|----------|----------------------------------------------------------|
| 阶段     | 11（策略管理菜单与知识库绑定 / 后端）                    |
| 状态     | 已完成（待用户提交）                                     |
| 方案依据 | 原项目档案（stash：step-09-策略管理菜单与知识库绑定.md） |

## 1. 目标与范围

**目标**：① 策略版本管理落地（CHUNK 类型：列表/创建/编辑=复制新行/启停/删除，保存校验 40001）；
② 知识库可绑定切片策略（绑定表 + 查绑/绑/解绑接口 + 触发链绑定优先档位 + KB 展示绑定摘要）。

1. 策略 CRUD：`/strategy-versions` 列表（启用中/全部）/创建/编辑（复制新行）/启停/删除（有绑定引用 40452 禁删）；
2. 配置校验：CHUNK 分支走 `ChunkAlgorithmSpec.validate`（支持集/参数范围/不变量/互斥，非法 40001）；
3. KB 绑定：`GET/PUT /knowledge-base/{id}/strategy-binding`；解绑逻辑删除、重绑复用原行；审计 BIND；
4. 触发链四档：显式传参 > KB 绑定（开关开启，失效回退告警）> 全局最新启用 > 内置默认；
5. KB 展示：详情/列表填充 chunkStrategyVersionId/chunkStrategyVersion 摘要。

**不做什么**：PREPROCESS/EMBED/RETRIEVAL 类型管理（随阶段 12/13/17）；批量绑定接口 bindStrategies（执行链工作台，随阶段 14）；前端策略管理独立菜单、KB 表单下拉与旧入口跳转（随阶段 18）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                   | 依据     |
|-------|--------------------------------------------------------------------------------------------------------|----------|
| D11-1 | SUPPORTED_TYPES 本阶段仅 CHUNK（其余类型随环节实现加入），避免引用后续阶段校验 Spec 造成编译悬空       | 用户拍板 |
| D11-2 | 批量绑定 bindStrategies 推迟到阶段 14（执行链工作台落地时一并交付）                                    | 用户拍板 |
| D11-3 | 前端随阶段 18；本阶段纯后端                                                                            | 用户拍板 |
| D11-4 | KbStrategyBindingDbService/Impl 按 InfraDbService 模式收口（不直接 extends MP IService，对齐项目约定） | 项目约定 |
| D11-5 | 策略版本行不可变：编辑=复制新行、停用代替删除、有绑定引用 40452 禁物理删除                             | B0 口径  |

## 3. 任务清单

1. common：`KbStrategyBinding` 实体 + `StrategyVersionCreateDto/UpdateDto/VO` + `StrategyBindingUpdateDto/VO` 纳入；`ErrorCode.STRATEGY_BOUND_DELETE_FORBIDDEN(40452)`；`AuditActionType.BIND`；`KnowledgeBaseRules.isStrategyBindingEnabled`；`KnowledgeBaseVO` 增 chunkStrategyVersionId/chunkStrategyVersion；
2. biz 数据层：`KbPipelineStrategyVersionDbService(+Impl)` 增 listByType/listEnabledByType；`KbStrategyBindingDbService(+Impl)`（InfraDbService 模式）+ `KbStrategyBindingMapper`；
3. biz 策略 CRUD：`StrategyVersionService/Impl/Controller`（R 导入修正；validateConfig 仅 CHUNK 分支）；
4. biz KB 绑定：`KnowledgeBaseService/Impl/Controller` 增查绑/绑/解绑（校验链 + 审计 BIND + 详情/列表填充绑定摘要）；
5. 触发链：`ChunkControlServiceImpl.resolveStrategy` 插 KB 绑定档位（开关 + ACTIVE 校验 + 失效回退告警）；
6. SQL：执行 stage-11-策略绑定.sql（kb_strategy_binding 建表）；
7. 单测：StrategyVersionServiceImplTest 重写为 CHUNK 范围 + KbPipelineStrategyVersionDbServiceImplTest 增列表用例 + KnowledgeBaseServiceImplTest 增绑定用例 + ChunkControlServiceImplTest 增绑定档用例；
8. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + worker 184 + biz 151 = 352）
- [x] 隔离验证：后续阶段文件移出（140）→ 全绿 → 恢复零丢失
- [x] stage-11 SQL 已执行（kb_strategy_binding 建表核验）
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调）：KB 绑定策略后触发切片走绑定策略；策略管理接口增删改查

## 5. 执行记录

| 任务          | 执行结果 | 备注                                                                                                                                                                                                  |
|---------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 common 契约 | 完成     | 实体/DTO/VO 纳入 + 40452 + BIND + isStrategyBindingEnabled + VO 两字段                                                                                                                                |
| 2 数据层      | 完成     | 策略版本列表两方法 + 绑定 DbService 收口 InfraDbService 模式                                                                                                                                          |
| 3 策略 CRUD   | 完成     | 三件套只 CHUNK 校验；R 导入修正（com.knowledge.common.core.util.R）                                                                                                                                   |
| 4 KB 绑定     | 完成     | 查绑/绑/解绑 + 审计 BIND + 详情/列表摘要填充                                                                                                                                                          |
| 5 触发链      | 完成     | 四档解析 + 失效回退告警                                                                                                                                                                               |
| 6 SQL         | 完成     | docker 执行并核验建表（uk(knowledge_base_id, strategy_type)）                                                                                                                                         |
| 7 单测        | 完成     | 新增/改造 36 例（StrategyVersion 21 + DB 4 + KB 绑定 8 + 触发链 3）；修 2 处：显式优先用例多余桩触发 UnnecessaryStubbing → 移除绑定档桩；无引用方法 getEnabledByTypeAndVersion 删除（0 warning 收口） |
| 8 文档        | 完成     | 本档案                                                                                                                                                                                                |
