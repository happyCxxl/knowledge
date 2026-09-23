# 阶段 4：文件服务与文档输入（实施文档）

> 约定：每个实施阶段在 `` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                                                                    |
|----------|---------------------------------------------------------------------------------------------------------|
| 阶段     | 4（文件服务与文档输入）                                                                                 |
| 状态     | 已完成（待用户提交）                                                                                    |
| 方案依据 | 原项目档案（stash：step-02 文档输入 / 01文档输入-环节交接 / 文档输入实现文档）；本次新增决策见下方 D4-x |

## 1. 目标与范围

**目标**：以自建文件服务模块 `knowledge-file-center` 替代原外部文件服务，并落地文档输入闭环——上传建档、提交校验、建档三写与查询接口。

1. `knowledge-file-center` 模块（职责拆分：文件存取独立成模块）：MinIO 对象存储 + `kb_file_object` 档案表 + 上传/下载接口；
2. 提交链路：幂等检查（requestId）→ 知识库归属校验 → 文件校验（大小/空文件/双源比对/魔数白名单/损坏加密探测）→ 建档三写 + 提交留痕；
3. 查询接口：提交记录分页 + 文件结果分页（含各环节最新任务状态）；
4. 手动逐环节口径：提交只建档，不登记任务——解析由页面手动触发（任务衔接随阶段 5/6 落地）。

**不做什么**：

- 文档解析及后续环节（阶段 6 起）；
- 任务队列与消费循环（阶段 5）；
- 扫描件/纯图片（OCR 预留，入口拒绝并记录原因）。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                                                            | 依据                     |
|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|
| D4-1  | 文件服务模块命名 `knowledge-file-center`（职责拆分，与 knowledge 家族一致）；档案表 `kb_file_object`（knowledge 库，kb_ 前缀与其余业务表统一）；包名 `com.knowledge.filecenter` | 用户拍板                 |
| D4-2  | 文件字节存独立 MinIO 容器 `knowledge-minio`（桶 `files` 源文件 / `artifacts` 产物），不与 Milvus 内部 MinIO 共用                                                                | 用户拍板                 |
| D4-3  | 档案表与其余业务表同库（knowledge）；实体 `KbFileObject` 入 common（与 User 等实体同放，模块只留业务逻辑）                                                                      | 用户拍板                 |
| D4-4  | 产物按 sha256 内容寻址，不建档案表（哈希即地址，幂等复用）；业务台账由 knowledge 库 `kb_pipeline_product` 记（阶段 6 建表）                                                     | 用户确认                 |
| D4-5  | 上传即建档：POST /files → MinIO 落对象 + kb_file_object 入库 + 返回 fileId；提交时按 fileId 复用 kb_source_file                                                                 | 原设计兼容               |
| D4-6  | 去租户与归属字段：kb_source_file/kb_file_result/kb_submit_log 无 tenant_id；kb_file_result 无 owner，归属只留 user_id                                                           | 用户拍板（无租户概念）   |
| D4-7  | 提交人/上传人 = 当前登录用户（SecurityUtils），无平台 token 概念                                                                                                                | 阶段 3 口径延续          |
| D4-8  | 校验链经 `FileStorage`（knowledge-file-center）开流，双源比对口径 = 流式统计 vs kb_file_object 档案大小                                                                         | 替代原外部文件服务元数据 |
| D4-9  | 文件结果列表 stage 参数只做合法性校验（40001）；按上游产物过滤随解析环节（产物表）落地后启用                                                                                    | 阶段 4 无产物表          |
| D4-10 | 存储 API 形态：门面 `FileStorage`（统一入口，方法按 fileId/sha256 寻址分组）+ 存储提供者 `StorageProvider`（provider 包，实现可替换）+ 模板基类（摘要写入骨架去重）             | 用户拍板                 |

## 3. 任务清单

### 任务 1：knowledge-file-center 模块

- 新建模块：pom（common + MP + MinIO SDK + web）、`FileCenterConfig`、`MinioClientConfig`（客户端 + 桶初始化）、`KbFileObject` 实体（common）、`KbFileObjectMapper`/`KbFileObjectDbService(+Impl)`、门面 `FileStorage`（service 包，方法按 fileId/sha256 寻址分组）+ 存储提供者 `StorageProvider`（provider 包）+ 实现 `MinioFileStorage`（service/impl）/`MinioStorageProvider`（provider，模板基类去重）、`FileService(+Impl)`（下载写响应）、`FileController`（POST /files 上传、GET /files/{fileId} 下载，薄转发）；
- 上传：雪花 fileId（IdWorker）= MinIO 对象键；流式 sha256；档案入库（create_by=上传人）。

### 任务 2：common 领域模型（阶段 4 部分）

- 实体：KbFileObject/KbSourceFile/KbFileResult/KbSubmitLog/KbPipelineTask（去 tenant/owner；KbSubmitLog sha256/fileName IGNORED 策略保留）；
- 值对象：domain/input（FileReference/FileMetadata/FileValidationResult/FileCheckResult）；
- 枚举：input（SubmitStatus/FileFormat/FileValidationFailReason/FileCheckStepName）、task（PipelineStage/PipelineTaskStatus，裁剪检索预留项）；
- DTO：FileSubmitRequest / FileSubmitResponse / SubmitLogVO / FileResultVO / StageStatusVO；
- 规则：StageFunnelRules + KnowledgeBaseRules.checkCanSubmit；
- 错误码：40410~40416、40420、40421；
- JsonUtil（common/utils，快照序列化用）。

### 任务 3：worker 校验链改造

- `FileValidationPipeline` 换用 `FileStorage`（取档案/开流），文件不存在 → fail(FILE_NOT_FOUND)；
- 五校验步骤、SpillBuffer、FileValidationProperties 保留口径（注释清理）；
- 删除 Feign 文件适配（adapter/filemanager、adapter/artifact 及其测试）；Tika 3.1.0 依赖保留。

### 任务 4：biz 提交链路

- `KnowledgeFileInputController` 三接口（submit / submit-logs / file-results）；
- `FileSubmitService(+Impl)`：幂等（预查 + uk 兜底 + DuplicateKey 回放）→ 归属（40401/40421）→ 校验（FAIL 日志正常返回）→ 建档三写（@Transactional，不登记任务）；
- 4 套 Mapper/DbService 收口 InfraDbService 模式（任务表只留阶段内方法）；
- `InputVoAssembler`/`TaskVoAssembler` 纯映射；`@MapperScan` 增加 file-center.mapper。

### 任务 5：SQL 与基础设施

- `stage-04-文档输入.sql`：knowledge 库建五表（`kb_file_object` + 文档输入四表，去 tenant/owner），已执行落库；
- 其余脚本按新阶段号改名并清理头部注释；
- compose 新增 `knowledge-minio`（9000/9001，独立数据卷）。

### 任务 6：验证

- 隔离验证：`mvn test` 全绿（common 4 + auth 8 + worker 14 + biz 37 = 63）；
- 文件恢复与 stash 对照零丢失。

## 4. 验收标准

- [x] `kb_file_object` 档案表已建（含 COMMENT）
- [x] 文档输入四表已建（无 tenant_id/owner）
- [x] `mvn test` 全绿（63 个用例）
- [x] 隔离文件全部恢复，与 stash 对照零丢失
- [ ] 真实冒烟：上传 → 提交 → 建档核对（待应用启动后补验）

## 5. 执行记录

| 任务                              | 执行结果                                                             | 备注                                      |
|-----------------------------------|----------------------------------------------------------------------|-------------------------------------------|
| 任务 1 knowledge-file-center 模块 | 完成：14 个文件（pom + 门面/提供者/档案访问/下载服务/控制器）        | 上传流式 sha256（DigestInputStream 单遍） |
| 任务 2 common                     | 完成：实体×5、值对象×4、枚举×6、DTO×5、规则×2、错误码+9、JsonUtil    | 去 tenant/owner；裁剪检索预留枚举         |
| 任务 3 worker                     | 完成：校验链 11 文件改写 + 删除适配器 9 文件                         | FileStorage 替代 Feign                    |
| 任务 4 biz                        | 完成：控制器/服务/4 套 DbService/2 Assembler 共 18 文件              | InfraDbService 模式收口                   |
| 任务 5 SQL/基础设施               | 完成：stage-04 落库（5 表）；7 个脚本改名；compose + knowledge-minio | 独立 MinIO 未启动（随用户环境）           |
| 任务 6 验证                       | 完成：63/63 全绿；恢复核对零丢失                                     | 首次编译失败为缺 InputStream 导入，已修复 |

## 6. 下一步预告

阶段 5：任务衔接与文档输入改造（Redis 队列、消费契约、流式校验改造、格式白名单收口）。
