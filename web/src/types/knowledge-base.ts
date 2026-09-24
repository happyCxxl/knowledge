// 知识库卡片展示的业务字段
export type KnowledgeBaseStatus = 'running' | 'building' | 'stopped';

export interface KnowledgeBase {
  /** 知识库主键（后端雪花 Long 以字符串下发） */
  id: string;
  /** 知识库名称 */
  name: string;
  /** 用途说明 */
  description: string;
  /** 向量维度 */
  dimension: number;
  /** 文档数量 */
  documentCount: number;
  /** 运行状态 */
  status: KnowledgeBaseStatus;
  /** 最近更新时间（ISO 字符串） */
  updatedAt: string;
}
