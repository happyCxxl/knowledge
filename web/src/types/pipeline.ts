/** 处理链环节（与后端 PipelineStage 枚举对齐；BUILD_INDEX 及之后属另外的视图） */
export const PIPELINE_STAGES = ['PARSE', 'STRUCTURE', 'PREPROCESS', 'CHUNK', 'EMBED'] as const;

export type PipelineStage = (typeof PIPELINE_STAGES)[number];

/**
 * 环节中文名。
 *
 * <p>用索引签名而非 Record&lt;PipelineStage, string&gt;：后端可能返回本端尚未枚举的环节，
 * 断言类型会让 TS 以为查表必定命中，实际会渲染出 undefined。
 */
export const STAGE_LABELS: Record<string, string> = {
  PARSE: '解析',
  STRUCTURE: '组装',
  PREPROCESS: '预处理',
  CHUNK: '切片',
  EMBED: '向量化',
};

/** 取环节展示名；未知环节回落原值，避免渲染出 undefined */
export function stageLabel(stage: string): string {
  return STAGE_LABELS[stage] ?? stage;
}

/** 任务状态（与后端 PipelineTaskStatus 枚举对齐） */
export type PipelineTaskStatus =
  'QUEUED' | 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELLED';

/** 任务状态中文名（同样用索引签名，未知状态回落原值） */
export const TASK_STATUS_LABELS: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '运行中',
  SUCCESS: '成功',
  PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败',
  CANCELLED: '已取消',
};

/** 取任务状态展示名；未知状态回落原值 */
export function taskStatusLabel(status: string): string {
  return TASK_STATUS_LABELS[status] ?? status;
}

/**
 * 任务终态：进入这些状态后不会再变化，轮询即可停止。
 *
 * <p>用白名单而非「是否 RUNNING」判断：任务可能长期处于 QUEUED，也可能直接进入
 * PARTIAL_SUCCESS / FAILED。只排除 RUNNING 会漏判，导致轮询永不停止。
 */
export const TERMINAL_TASK_STATUSES = new Set<string>([
  'SUCCESS',
  'PARTIAL_SUCCESS',
  'FAILED',
  'CANCELLED',
]);

/** 任务是否仍在推进（未进入终态） */
export function isTaskPending(status: string | null | undefined): boolean {
  if (!status) {
    // 状态未知时按未完成处理，给后端回填状态留时间
    return true;
  }
  return !TERMINAL_TASK_STATUSES.has(status);
}

/** 状态视觉归类：ok 成功 / run 进行中 / wait 排队 / fail 失败 */
export type StatusTone = 'ok' | 'run' | 'wait' | 'fail';

export function statusTone(status: string | null | undefined): StatusTone {
  switch (status) {
    case 'SUCCESS':
      return 'ok';
    case 'RUNNING':
      return 'run';
    case 'PARTIAL_SUCCESS':
      return 'wait';
    case 'FAILED':
    case 'CANCELLED':
      return 'fail';
    default:
      return 'wait';
  }
}

/** 文件某项环节的最新任务状态（后端 StageStatusVO；无任务的环节不返回该条目） */
export interface StageStatus {
  stage: string;
  taskId: string | null;
  status: string | null;
  errorCode: string | null;
  errorMsg: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

/** 文件结果（后端 FileResultVO）：执行链列表数据源 */
export interface FileResult {
  id: string;
  knowledgeBaseId: string;
  sourceFileId: string;
  fileId: string;
  fileName: string;
  fileSize: number | null;
  createTime: string | null;
  /**
   * 各环节最新任务状态（无任务的环节不出现）。
   *
   * <p>**可空**：全新导入、尚未跑过任何环节的文件，后端返回 null 而不是空数组
   * （实测 `stageStatuses: null`）。取值一律走 {@link stageStatusList}，直接当数组用会抛异常。
   */
  stageStatuses: StageStatus[] | null;
}

/** 安全取环节状态列表：后端可能返回 null（新导入文件），统一按空数组处理 */
export function stageStatusList(file: FileResult): StageStatus[] {
  return file.stageStatuses ?? [];
}

/**
 * 能力快照（后端 LineageCapabilityVO）。
 *
 * <p>**是对象而不是 JSON 串**：后端已把 product.capabilitySnapshot 的 JSON 文本解析开，
 * 前端不需要（也不应该）自己解析。
 */
export interface LineageCapability {
  /** 原生解析器名称（如 pdfbox） */
  parserName: string | null;
  /** 原生解析器版本（如 3.0.4） */
  parserVersion: string | null;
  /** OCR 能力（一期预留，未接入时为 null） */
  ocr: CapabilityRef | null;
  /** 版面分析能力（一期预留） */
  layout: CapabilityRef | null;
  /** 表格识别能力（一期预留） */
  table: CapabilityRef | null;
}

/** 能力引用（模型名 + 版本） */
export interface CapabilityRef {
  model: string | null;
  version: string | null;
}

/** 执行链节点（后端 LineageNodeVO）：一个环节的一次任务运行 */
export interface LineageNode {
  taskId: string;
  /** 环节（PipelineStage 枚举名） */
  stage: string;
  status: string;
  errorCode: string | null;
  errorMsg: string | null;
  /** 策略版本（PREPROCESS/CHUNK/EMBED 有；PARSE/STRUCTURE 为空） */
  strategyVersion: string | null;
  /** 能力快照（PARSE/STRUCTURE 有；有策略的环节为空） */
  capability: LineageCapability | null;
  /** 该次运行产出的产物 ID（成功有产物时非空） */
  productId: string | null;
  artifactId: string | null;
  contentHash: string | null;
  /** 统计摘要（原样透传展示）：CHUNK=chunkCount、EMBED=recordCount/cachedCount 等 */
  stats: Record<string, string> | null;
  startedAt: string | null;
  finishedAt: string | null;
}

/**
 * 统计键名 → 中文标签。
 *
 * <p>键名由后端各环节自定义（不可控），映射放前端：后端保持"机器可读的键"，
 * 展示文案归前端，这样加环节时前端明确知道自己有没有漏配。
 * 未在表里的键回落显示原键名（不会空白）。
 */
const STAT_LABELS: Record<string, string> = {
  chunkCount: '切片数',
  recordCount: '向量数',
  cachedCount: '命中缓存',
  matched: '匹配',
  changed: '改动',
};

/** 统计项：中文标签 + 值。键名未映射时标签回落原键名 */
export function statEntry(key: string, value: string): { label: string; value: string } {
  return { label: STAT_LABELS[key] ?? key, value };
}

/**
 * 能力快照 → 一行可读文案（如 `pdfbox 3.0.4`）。
 *
 * <p>接入 ocr/layout/table 后追加它们的名称，一期三者均为 null 所以不出现。
 * 全空时返回 null，让调用方决定显示什么兜底。
 */
export function capabilityText(capability: LineageCapability | null): string | null {
  if (!capability) {
    return null;
  }
  const parts: string[] = [];
  const parser = [capability.parserName, capability.parserVersion].filter(Boolean).join(' ');
  if (parser) {
    parts.push(parser);
  }
  for (const ref of [capability.ocr, capability.layout, capability.table]) {
    if (ref?.model) {
      parts.push([ref.model, ref.version].filter(Boolean).join(' '));
    }
  }
  return parts.length > 0 ? parts.join(' · ') : null;
}

/** 执行链血缘边（后端 LineageEdgeVO） */
export interface LineageEdge {
  fromTaskId: string;
  toTaskId: string;
}

/** 执行树聚合（后端 LineageVO） */
export interface Lineage {
  fileResultId: string;
  nodes: LineageNode[];
  edges: LineageEdge[];
}

/**
 * 触发环节执行的返回（后端 StageTriggerVO / PreprocessTriggerVO 等）。
 *
 * <p>接口是**异步**的：只返回新任务的 ID，执行结果要另行轮询 lineage 获取。
 */
export interface StageTriggerResult {
  pipelineTaskId: string | null;
  fileResultId?: string;
  /** 实际使用的策略版本（仅预处理/切片/向量化返回），可核对是否与预期一致 */
  strategyVersion?: string | null;
}

/**
 * 执行树节点的渲染数据（Vue Flow 自定义节点的 data 载荷）。
 *
 * <p>放在这里而不是组件内，是因为图组件与节点组件都要用到同一份定义。
 */
export interface ChainNodeData {
  /** 该节点对应的运行 */
  node: LineageNode;
  /** 是否在用户选中的路径上 */
  onPath: boolean;
  /** 是否是选中路径的末端（从这里触发下游） */
  pathEnd: boolean;
  /** 是否命中知识库当前绑定的策略 */
  hit: boolean;
  /** 出度（下游运行数）：决定节点右侧铺几个源连接桩，避免多次触发的边起点重叠 */
  outCount: number;
  /**
   * 点击卡片上「触发下一环节」时的回调。
   *
   * <p>**为什么用回调而不是 emits**：Vue Flow 的自定义节点是它内部渲染的，
   * `emit` 到不了页面组件，得先经 `ChainGraph` 再转发一层；而回调由 `ChainGraph`
   * 在组装 `data` 时注入，是它自己的方法（不需要 `getCurrentInstance`），
   * 链路更短也更好追。
   */
  onTrigger?: () => void;
}

/** 节点在图上的坐标 */
export interface NodePosition {
  x: number;
  y: number;
}

/**
 * 各文件的节点坐标记忆。
 *
 * <p>由**页面持有**（而不是图组件内部）：切文件时图组件可能被重建，
 * 而且 `selectedFileId` 立即变、`lineage` 要等接口返回，两者之间存在竞态窗口，
 * 组件内无法可靠判断"这份坐标属于哪个文件"。挂在 key 上就没有归属歧义。
 */
export type NodePositionStore = Map<string, Map<string, NodePosition>>;

/** 知识库-策略绑定（后端 StrategyBindingVO；未绑定时只有 strategyType） */
export interface StrategyBinding {
  /**
   * 知识库 ID。
   *
   * <p>**单体查询**（`GET /knowledge-base/{id}/strategy-binding`）时为 null ——
   * 库 ID 由请求路径给出，返回里不重复；**批量查询**
   * （`GET /knowledge-base/strategy-bindings`）时必有值，是区分各行的标识。
   */
  knowledgeBaseId: string | null;
  strategyType: string;
  strategyVersionId: string | null;
  strategyName: string | null;
  /** 仅版本号（如 v1） */
  strategyVersion: string | null;
}

/**
 * 批量设置绑定的一项（后端 `StrategyBindingsUpdateRequest.StrategyBindItem`）。
 *
 * <p>批量接口要求给全 {@link STRATEGY_BINDING_TYPES} 且每项 `strategyVersionId` 都非空。
 */
export interface StrategyBindItem {
  strategyType: string;
  strategyVersionId: string;
}

/** 有策略的环节及其绑定类型（解析/组装无策略，不参与绑定比对） */
export const STRATEGY_BINDING_TYPES = ['PREPROCESS', 'CHUNK', 'EMBED'] as const;
