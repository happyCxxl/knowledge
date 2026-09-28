/**
 * 首页的类型：资产速览 + 最近提交 + 行为记录。
 *
 * <p>两类"最近发生"分开：
 *
 * <ul>
 *   <li>**最近提交**（`kb_submit_log`）—— 日常文件提交，status 是**校验结果** PASS/FAIL；</li>
 *   <li>**行为记录**（`kb_audit_log`）—— 管理员关键操作，不含文件提交。</li>
 * </ul>
 *
 * <p>动作与对象的中文名由后端下发 —— 动作有 15 种，前端维护映射表会与后端漂移。
 */

/** 资产速览（后端 `HomeSummaryVO`）：每类都给细分，避免为看细分再点进对应页 */
export interface HomeSummary {
  knowledgeBaseCount: string;
  enabledKnowledgeBaseCount: string;
  /** 策略版本总数（四类合计，含已停用） */
  strategyVersionCount: string;
  preprocessVersionCount: string;
  chunkVersionCount: string;
  embedVersionCount: string;
  retrievalVersionCount: string;
  indexVersionCount: string;
  /** 在线索引版本数（按发布指针命中，正常为 0 或 1） */
  onlineIndexVersionCount: string;
  /** 文档提交总数（一次提交 = 一个任务 = 一行） */
  documentCount: string;
}

/** 最近提交一行（后端 `HomeRecentSubmitVO`） */
export interface HomeRecentSubmit {
  id: string;
  /** 文件名（文件不存在等场景可能为空串） */
  fileName: string;
  knowledgeBaseId: string;
  /** 所属知识库名（后端反查；已删库回落成「知识库 {ID}」） */
  knowledgeBaseName: string;
  /** 提交人用户名 */
  operator: string | null;
  /** 提交结果：PASS / FAIL */
  status: string;
  /** 失败原因码值（PASS 时为空） */
  failReason: string | null;
  /** 失败原因中文名（后端下发） */
  failReasonLabel: string | null;
  createTime: string;
}

/** 最近提交的查询条件 */
export interface HomeRecentSubmitQuery {
  current: number;
  size: number;
}

/** 行为记录一行（后端 `HomeActivityVO`） */
export interface HomeActivity {
  id: string;
  /** 动作枚举名（用于按类型着色） */
  actionType: string;
  /** 动作中文（后端下发） */
  actionLabel: string;
  objectType: string;
  objectTypeLabel: string;
  objectId: string;
  /** 对象名（后端按类型反查；索引版本会回填为「{知识库名} {版本号}」） */
  objectName: string;
  /** 变更前（可读文本，可空） */
  beforeSummary: string | null;
  /** 变更后（可读文本，可空） */
  afterSummary: string | null;
  /** 操作人用户名（无认证上下文为 system） */
  operator: string | null;
  createTime: string;
}

/** 行为记录的查询条件 */
export interface HomeActivityQuery {
  current: number;
  size: number;
  /** 动作类型；不传不过滤 */
  actionType?: string;
  /** 操作人用户名；不传不过滤 */
  operator?: string;
  /** 起始时间，格式 yyyy-MM-dd HH:mm:ss */
  beginTime?: string;
  /** 结束时间，格式 yyyy-MM-dd HH:mm:ss */
  endTime?: string;
}

/**
 * 动作的色调分类。
 *
 * <p>只区分「发布类（正向）/ 回收删除类（破坏性）/ 其余（中性）」三档 ——
 * 15 种动作各配一个颜色反而看不出重点。
 *
 * <p>色调到样式类的映射放在模板里（写成字面量映射表）：
 * 样式检查脚本只认模板中的字面类名，靠函数返回类名会被判成"样式类未使用"。
 */
const ACTIVITY_TONES: Record<string, ActivityTone> = {
  PUBLISH_INDEX: 'ok',
  ROLLBACK_INDEX: 'ok',
  RETRIEVAL_RULE_PUBLISH: 'ok',
  RECYCLE_INDEX: 'danger',
  DELETE: 'danger',
  USER_DELETE: 'danger',
  DISABLE: 'danger',
  USER_DISABLE: 'danger',
};

/** 色调：ok 正向 / danger 破坏性 / plain 中性 */
export type ActivityTone = 'ok' | 'danger' | 'plain';

export function activityTone(actionType: string): ActivityTone {
  return ACTIVITY_TONES[actionType] ?? 'plain';
}

/** 时间展示：`MM-DD HH:mm`（首页只关心近期，年份冗余） */
export function formatActivityTime(value: string | null): string {
  if (!value) {
    return '—';
  }
  const normalized = value.replace('T', ' ');
  return normalized.length >= 16 ? normalized.slice(5, 16) : normalized;
}

/**
 * 变更文本：把 before/after 压成一句可读的变更。
 *
 * <p>三种情况：发布类（旧 → 新）、创建类（只有后值）、删除类（只有前值）。
 * 两边都有但相同时（重复发布同一版本）只显示一次，避免 `v3 → v3` 这种噪音。
 */
export function deltaText(item: Pick<HomeActivity, 'beforeSummary' | 'afterSummary'>): string {
  const before = item.beforeSummary?.trim() ?? '';
  const after = item.afterSummary?.trim() ?? '';
  if (before && after) {
    return before === after ? before : `${before} → ${after}`;
  }
  return after || before;
}
