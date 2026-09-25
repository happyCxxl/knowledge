// 知识库状态码值（与后端 KnowledgeBaseStatus 对齐）
export const KB_STATUS_ACTIVE = 1;
export const KB_STATUS_DISABLED = 0;

/** 知识库卡片展示的业务字段（对齐后端 KnowledgeBaseVO） */
export interface KnowledgeBase {
  /** 知识库主键（后端雪花 Long 以字符串下发） */
  id: string;
  /** 知识库名称 */
  name: string;
  /** 业务场景说明 */
  description: string | null;
  /** 状态码值：1 启用 / 0 停用 */
  status: number;
  /**
   * 文档总数：kb_file_result 记录数。
   * 一次提交 = 一个任务 = 一行，同一文件重复提交会各占一行，故为提交次数而非去重文件数。
   */
  documentCount: number | null;
  /** 绑定的向量化策略版本摘要（如 embed-default-v1；无绑定为 null） */
  embedStrategyVersion: string | null;
  /** 策略绑定开关：1 开启（触发走本库绑定策略）/ 0 关闭（测评模式，触发须显式选策略） */
  strategyBindingEnabled: number | null;
  /** 默认知识库标记：1=默认库（恒排最前、不可停用/删除）/ 0=普通库 */
  defaultFlag: number;
  /** 当前已发布索引版本号（如 v3；未发布为 null） */
  publishedIndexVersion: string | null;
  /** 最近更新时间（ISO 字符串） */
  updateTime: string | null;
}

/** 知识库统计概览（仅统计未删除数据） */
export interface KnowledgeBaseStats {
  /** 知识库总数 */
  knowledgeBaseCount: string;
  /** 启用中的知识库数 */
  enabledCount: string;
  /** 文档总数（提交任务数） */
  documentCount: string;
}

/** 列表排序口径（与后端 KnowledgeBaseSort 对齐；默认库在任何口径下都恒排最前） */
export type KnowledgeBaseSort = 'DEFAULT' | 'UPDATED' | 'NAME';

/** 知识库分页入参 */
export interface KnowledgeBasePageQuery {
  current: number;
  size: number;
  /** 名称模糊关键字 */
  name?: string;
  /** 状态过滤：1 启用 / 0 停用；不传不过滤 */
  status?: number;
  /** 排序口径；不传按后端默认（默认库最前 + id 倒序） */
  sort?: KnowledgeBaseSort;
}

/** 创建知识库入参（后端一律落普通库并默认启用，状态与策略绑定不在此处） */
export interface KnowledgeBaseCreateRequest {
  /** 名称，必填，≤128 */
  name: string;
  /** 业务场景说明，≤512 */
  description?: string;
  /** 策略绑定开关：1 开启（默认）/ 0 关闭 */
  strategyBindingEnabled?: number;
}

/** 更新知识库入参（仅名称/说明/策略绑定开关；停用启用有独立接口） */
export interface KnowledgeBaseUpdateRequest {
  /** 知识库 ID（后端 DTO 要求，与路径参数一致） */
  id: string;
  /** 名称，必填，≤128 */
  name: string;
  /** 业务场景说明，≤512 */
  description?: string;
  /** 策略绑定开关：1 开启 / 0 关闭 */
  strategyBindingEnabled?: number;
}
