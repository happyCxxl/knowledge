-- 阶段 20 追加：kb_chunk 删除 source_element_ids 列 —— 执行一次
-- ----------------------------------------------------------------------------
-- 背景：该列存切片溯源（元素 ID 的 JSON 数组），但它有两个问题：
--
--   1. 长度无界：父片溯源 = 该章节全部子片的元素 ID 并集，随章节大小增长。
--      实测一份 116 页招标文件（512 个元素）的单个父片溯源为 3503 字符，
--      远超本列 varchar(1024) 的容量，导致整批切片落库失败
--      （Data too long for column 'source_element_ids'）。
--
--   2. 纯冗余：检索链路的溯源取自 Milvus 集合，而 Milvus 的行由
--      IndexRowAssembler 从切片产物（kb_chunk_set 的 artifact）读取装配，
--      不经过本列。本列只是给详情接口用的副本，两处口径还可能不一致。
--
-- 处置：溯源只在「切片产物」与「Milvus 集合」中保存（两者都无 1024 限制）；
--       MySQL 不再保存该列，详情接口也不再回传该字段。
--
-- 注意：Milvus 集合的 source_element_ids 字段长度同时从 2048 提升到 16384
--      （见 MilvusKnowledgeCollection.SOURCE_ELEMENT_IDS_MAX_LENGTH）。
--      已建集合需重建才能生效；开发阶段直接删集合重建即可。
-- ----------------------------------------------------------------------------

use knowledge;

ALTER TABLE kb_chunk
    DROP COLUMN source_element_ids;
