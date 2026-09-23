# 阶段 15：向量库模块 knowledge-vector（实施文档）

> 约定：每个实施阶段在 `docs/steps/` 落一份实施文档，记录目标、决策、任务清单、验收标准与执行结果。

## 元信息

| 项       | 内容                                                |
|----------|-----------------------------------------------------|
| 阶段     | 15（向量库模块 knowledge-vector / Milvus 封装下沉） |
| 状态     | 已完成（待提交）                                    |
| 方案依据 | 实施手册 §5 阶段 15 自实现项（Milvus 封装下沉）     |

## 1. 目标与范围

**目标**：把 Milvus SDK 的使用从 worker 索引环节下沉到独立模块 `knowledge-vector`——客户端生命周期、集合管理（schema/索引/BM25 函数）、查询封装与口径钉版全部收口，业务层（worker 索引 Port）退化为薄壳适配器。

1. **新建模块 knowledge-vector**：
   - `MilvusProperties`（knowledge.vector.milvus 前缀：host/port/database/enable/超时，Nacos 可覆盖）；
   - `MilvusClientFactory`（客户端 Bean 生命周期：创建/关闭；enable=false 不注册，索引/检索环节跳过）；
   - `KnowledgeCollectionPort` + `MilvusKnowledgeCollection`（集合生命周期 + 行读写 + 查询封装；口径钉版：全文通道 content/title_path（BM25 + chinese analyzer）、标量倒排 document_id/owner/content_type、向量通道 HNSW+COSINE、STRONG 一致性）；
   - 行类型 `CollectionRow`/`ScoredRow`（与集合 schema 一一对应）；
2. **worker 适配**：`MilvusIndexPort` 业务契约不变；`MilvusIndexPortImpl` 改为薄壳适配器（业务行 ↔ 向量库行映射、业务过滤拼装），删除 IndexCollectionManager（生命周期逻辑下沉）；
3. **契约**：`ErrorCode.INDEX_COLLECTION_SCHEMA_MISMATCH(40447)`（schema 回读校验失败）。

**不做什么**：索引构建/发布/组合模型（阶段 16）；检索与评测（阶段 17）；biz 对 Milvus 的任何直接依赖。

## 2. 本阶段决策（记录）

| 编号  | 决策                                                                                                                                         | 依据                            |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| D15-1 | Milvus 封装独立成 knowledge-vector 模块（端口 + 实现 + 行类型 + 客户端工厂），SDK 版本随根 pom 钉版（2.5.5）                                 | 实施手册 §5                     |
| D15-2 | 端口按集合名寻址的通用操作集（ensure/load/drop/upsert/searchVector/searchFullText/listIds/queryByIds），口径（schema/索引/一致性）钉在模块内 | 自实现项口径                    |
| D15-3 | worker 的 MilvusIndexPort 保留为业务契约，实现退化为适配器；IndexCollectionManager 删除                                                      | 分层收口                        |
| D15-4 | 客户端开关 knowledge.vector.milvus.enable（默认开）；关闭时工厂不注册 Bean                                                                   | 参考 ConditionalOnProperty 口径 |

## 3. 任务清单

1. 新建 knowledge-vector 模块（pom + 7 主类 + 1 测试类）+ 根 pom 模块/依赖管理 + worker pom 依赖（删除 worker 的 milvus-sdk-java/gson 直接依赖，下沉后无直接 SDK 调用）；
2. common：ErrorCode 40447；
3. worker：MilvusIndexPortImpl 重写为适配器 + 删除 IndexCollectionManager 与其测试 + MilvusIndexPortImplTest 重写（mock KnowledgeCollectionPort）；
4. 单测：knowledge-vector MilvusKnowledgeCollectionTest 8 例（upsert schema 映射/空集跳过/向量检索映射/全文检索清洗/分页拉取/主键查询/空输入/关键词清洗）；
5. 文档：本档案。

## 4. 验收标准

- [x] `mvn test` 全绿（common 4 + infra 5 + auth 8 + model 3 + vector 8 + worker 225 + biz 198 = 451）
- [x] 隔离验证：后续阶段文件移出（65）→ 全绿 → 恢复零丢失
- [x] 无新 SQL
- [x] IDEA 无头检测：暂存/修改文件 0 warning
- [ ] 真实冒烟（可选，待联调 + Milvus 运行）：建集合 schema 回读校验/向量与全文检索/分页拉取

## 5. 执行记录

| 任务               | 执行结果 | 备注                                                                          |
|--------------------|----------|-------------------------------------------------------------------------------|
| 1 knowledge-vector | 完成     | 7 主类 + 测试 8 例；修 1 处：ConnectConfig 2.5.5 用 dbName（非 databaseName） |
| 2 common           | 完成     | 40447 补值                                                                    |
| 3 worker 适配      | 完成     | MilvusIndexPortImpl 薄壳化 + IndexCollectionManager 删除 + 测试重写 9 例      |
| 4 单测             | 完成     | 451 全绿                                                                      |
| 5 文档             | 完成     | 本档案                                                                        |
