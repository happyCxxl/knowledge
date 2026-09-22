# 阶段 3：核心领域模型与知识库管理闭环（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                                       |
|----------|----------------------------------------------------------------------------|
| 阶段     | 3（核心领域模型与知识库管理闭环）                                          |
| 状态     | 已完成（暂存待提交：`feat(knowledge-base): 核心领域模型与知识库管理闭环`） |
| 方案依据 | 原项目档案（stash：step-01）                                               |

## 1. 目标与范围

**目标**：知识库领域模型（实体/枚举/DTO）与管理闭环接口 + 操作审计（真实用户落账）。

1. common：`KnowledgeBase`/`KbAuditLog` 实体（无租户）、`KnowledgeBaseStatus`/`AuditActionType` 枚举（5 动作：CREATE/UPDATE/DISABLE/ENABLE/DELETE）、`KnowledgeBaseRules`（停用/启用/默认库保护校验）、知识库 DTO（CreateDto/UpdateDto/VO）、ErrorCode 增 40401/40402；
2. biz：`KnowledgeBaseController`（7 端点：分页/详情/创建/更新/停用/启用/删除）、`KnowledgeBaseService(+Impl)`（userId = 当前登录人）、Mapper×2/DbService×2（InfraDbService 模式收口）、操作审计落账（KbAuditLog）；
3. 接口文档：springdoc-openapi 接入 + SecurityConfig 放行 swagger 路径；
4. SQL `stage-03-知识库管理.sql`（kb_knowledge_base + seed 默认知识库 + kb_audit_log，去 tenant_id），已执行落库。

## 2. 本阶段决策（记录）

| 编号 | 决策                                                                           | 依据                 |
|------|--------------------------------------------------------------------------------|----------------------|
| D3-1 | 审计动作裁剪为 5 个（CREATE/UPDATE/DISABLE/ENABLE/DELETE），其余随后续环节扩展 | 裁剪口径             |
| D3-2 | 审计落账记录真实操作用户（userId + createBy 取当前登录人）                     | 阶段 2 口径延续      |
| D3-3 | 知识库实体/枚举/DTO/VO 集中 knowledge-common；knowledge-auth 只留业务逻辑      | 用户拍板（模块分工） |
| D3-4 | 默认知识库 seed 一行；default_flag 保护（不可停用/删除）                       | 原设计               |

## 3. 任务清单

1. common 领域模型（实体×2、枚举×2、规则、DTO×3、ErrorCode +2）；
2. biz 管理闭环（控制器/服务/Mapper/DbService 共 10 文件 + springdoc）；
3. SQL stage-03 执行落库（3 表 + seed）；
4. 单测（KnowledgeBaseRulesTest 6 + KbAuditLogDbServiceImplTest 3 + KnowledgeBaseDbServiceImplTest 2 + KnowledgeBaseServiceImplTest 12）。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + auth 8 + biz 23 = 35 用例）
- [x] 阶段清单内文件编译零错误
- [x] 3 张表 + seed 落库
- [x] 隔离文件恢复后与原始业务树对照零丢失

## 5. 执行记录

| 任务                | 执行结果 | 备注                                                                                      |
|---------------------|----------|-------------------------------------------------------------------------------------------|
| 领域模型 + 管理闭环 | 完成     | 25 文件暂存（+1333 行）                                                                   |
| SQL                 | 完成     | kb_knowledge_base/kb_audit_log + seed 落库                                                |
| 单测与验证          | 完成     | 35/35 全绿；KbAuditLogDbServiceImplTest 常量参数警告修复（authenticateAsOperator 无参化） |

## 6. 下一步预告

阶段 4：文件服务与文档输入（knowledge-file-center 模块 + 提交校验建档链路）。
