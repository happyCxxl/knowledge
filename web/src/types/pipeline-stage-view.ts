import type { LineageNode, ParseDetail, StructureDetail } from '@/types/pipeline';
import { formatCount, formatDuration, statNumber } from '@/types/pipeline';

/**
 * 环节展示配置：每个环节一份"怎么显示"的口径，卡片与详情抽屉都从这里取。
 *
 * <p>没有配置的环节一律显示中性内容（`—` / 空槽 / 无文案），**不回落成别的环节的口径** ——
 * 回落会让一个还没梳理的环节看起来"有数据"，比空着更容易误导。
 *
 * <p>条目本身不含任何后端调用：只做"取哪个字段、怎么格式化"的映射。
 */

/** 卡片指标行的一格 */
export interface StageMetric {
  key: string;
  /** 数值文案（缺失给 `—`） */
  value: string;
  /** 单位（无单位给空串） */
  unit: string;
}

/** 卡片构成图的一段 */
export interface StageComposeBar {
  /** 段标识（取值与条色类名一一对应） */
  type: string;
  /** 段名（只在悬停提示里给出） */
  label: string;
  /** 数值文案 */
  value: string;
  /** 条长（CSS 宽度） */
  width: string;
  /** 条色类名：类名状态检查看不到计算属性给出的名字，故由全局 chain-graph.css 提供 */
  fillClass: string;
}

/** 详情右栏页签 */
export type StageTabKey = 'elements' | 'tree' | 'warnings' | 'steps';

export interface StageTab {
  key: StageTabKey;
  label: string;
}

/** 详情统计条的一格 */
export interface StageStatItem {
  key: string;
  label: string;
  value: string;
}

/** 左栏内容类型：原文预览 / 组装产物文档 / 无 */
export type StageLeftPaneKind = 'source' | 'assembly' | 'none';

/** 体检项状态：绿=正常、黄=需要关注、红=有问题、灰=无数据 */
export type CheckTone = 'ok' | 'warn' | 'bad' | 'idle';

/** 卡片主体区的一行体检项 */
export interface StageCheckItem {
  key: string;
  /** 项目名（左侧；受卡片固定的标签列宽限制，用 4 个汉字以内） */
  label: string;
  /** 关键量（加粗） */
  value: string;
  /** 说明（弱化，可空） */
  note: string;
  tone: CheckTone;
}

/** 卡片主体区形态：构成条（解析）或体检清单（组装） */
export type StageBodyKind = 'bars' | 'checklist';

/** 一个环节的展示配置 */
export interface StageViewConfig {
  /** 进行态的状态文字（成功/失败等终态文字与环节无关，由公共表给） */
  runningText: string;
  /**
   * 卡片指标行（固定格数，缺失项给 `—`，行高与列位不变）。
   *
   * <p>`bodyKind = 'checklist'` 的环节**不再显示指标行**（信息已进体检清单），此处返回空数组。
   */
  metrics: (node: LineageNode) => StageMetric[];
  /** 卡片构成图（固定段数与顺序，缺失不给条）；仅 `bodyKind = 'bars'` 时渲染 */
  composeBars: (node: LineageNode) => StageComposeBar[];
  /** 主体区形态 */
  bodyKind: StageBodyKind;
  /** 体检清单（仅 `bodyKind = 'checklist'` 时渲染） */
  checklist: (node: LineageNode) => StageCheckItem[];
  /** 卡片摘要行文案（空串表示这一行留空） */
  summary: (node: LineageNode) => string;
  /** 详情右栏页签（顺序即展示顺序；至少一项，详情打开时默认落在第一项） */
  tabs: [StageTab, ...StageTab[]];
  /** 详情统计条 */
  statItems: (detail: ParseDetail | StructureDetail | null) => StageStatItem[];
  /** 详情左栏内容类型 */
  leftPaneKind: StageLeftPaneKind;
}

/** 构成图配色：段类型 → 条色类名（与 chain-graph.css 的 `.chain-node-bar-*` 对应） */
const FILL_CLASSES: Record<string, string> = {
  body: 'chain-node-bar-body',
  table: 'chain-node-bar-table',
  image: 'chain-node-bar-image',
  head: 'chain-node-bar-head',
};

const BAR_TYPES = ['body', 'table', 'image', 'head'] as const;
const BAR_LABELS = ['正文', '表格', '图片', '页眉页脚'];

/** 计数展示：缺失给 `—`（不回落 0，让"没有"和"是零"分得开） */
function countText(value: number | null | undefined): string {
  return formatCount(value);
}

/** 从节点通用统计里取一个计数（后端按数字下发） */
function statOf(node: LineageNode, key: string): number | null {
  const raw = node.stats?.[key];
  return statNumber(typeof raw === 'number' ? raw : null);
}

/**
 * 把若干计数画成构成图。
 *
 * <p>条长以 `total` 为分母；有值但占比极小时抬到 3px 下限，值 0 或缺失时不画条。
 */
function barsOf(
  types: readonly string[],
  labels: string[],
  counts: (number | null)[],
  total: number,
  failed: boolean,
): StageComposeBar[] {
  return types.map((type, index) => {
    const count = counts[index] ?? null;
    const ratio = failed || count === null || total <= 0 ? 0 : (count / total) * 100;
    const width = count !== null && count > 0 ? `max(3px, ${ratio.toFixed(1)}%)` : '0';
    return {
      type,
      label: labels[index] ?? type,
      value: failed ? '—' : countText(count),
      width,
      fillClass: FILL_CLASSES[type] ?? 'chain-node-bar-body',
    };
  });
}

/** 指标行通用取法：三项固定（页/元素/耗时；或环节自定义键） */
function metricRow(items: { key: string; value: string; unit: string }[]): StageMetric[] {
  return items;
}

/** 空的构成图：段位保留、不出条、数值给 `—`（未配置环节用） */
function emptyBars(): StageComposeBar[] {
  return BAR_TYPES.map((type, index) => ({
    type,
    label: BAR_LABELS[index] ?? type,
    value: '—',
    width: '0',
    fillClass: FILL_CLASSES[type] ?? 'chain-node-bar-body',
  }));
}

/** 解析环节：页数 / 元素数 / 耗时；构成图 = 四类元素 */
const PARSE_VIEW: StageViewConfig = {
  runningText: '解析中',
  bodyKind: 'bars',
  checklist: () => [],
  metrics: (node) =>
    metricRow([
      { key: 'pages', value: countText(node.parseStats?.pageCount), unit: '页' },
      { key: 'elements', value: countText(node.parseStats?.elementCount), unit: '元素' },
      { key: 'duration', value: formatDuration(node.parseStats?.durationMs), unit: '' },
    ]),
  composeBars: (node) => {
    const stats = node.parseStats;
    const counts = [
      statNumber(stats?.bodyCount),
      statNumber(stats?.tableCount),
      statNumber(stats?.imageCount),
      statNumber(stats?.headerFooterCount),
    ];
    // 条长以元素总数为分母；总数缺失时用四类之和
    const sum = counts.reduce<number>((acc, count) => acc + (count ?? 0), 0);
    const total = statNumber(stats?.elementCount) ?? sum;
    return barsOf(BAR_TYPES, BAR_LABELS, counts, total, node.status === 'FAILED');
  },
  summary: (node) => {
    if (node.status === 'RUNNING') {
      return '正在解析…';
    }
    return node.parseSummary ?? (node.status === 'FAILED' ? (node.errorMsg ?? '') : '');
  },
  tabs: [
    { key: 'elements', label: '元素' },
    { key: 'warnings', label: '告警' },
    { key: 'steps', label: '过程' },
  ],
  statItems: (detail) => {
    const stats = detail !== null && 'parseStats' in detail ? detail.parseStats : null;
    return [
      { key: 'pages', label: '页', value: countText(stats?.pageCount) },
      { key: 'elements', label: '元素', value: countText(stats?.elementCount) },
      { key: 'body', label: '正文', value: countText(stats?.bodyCount) },
      { key: 'table', label: '表格', value: countText(stats?.tableCount) },
      { key: 'image', label: '图片', value: countText(stats?.imageCount) },
      { key: 'head', label: '页眉页脚', value: countText(stats?.headerFooterCount) },
      { key: 'failed', label: '问题单元', value: countText(stats?.failedUnitCount) },
      {
        key: 'duration',
        label: '耗时',
        value: durationOf(detail, statNumber(stats?.durationMs)),
      },
    ];
  },
  leftPaneKind: 'source',
};

/**
 * 组装环节：主体区 = **结构体检清单**（不再是元素构成条）。
 *
 * <p>指标行返回空数组 —— 章节层数 / 标题数 / 元素数都在清单里，重复展示没有意义。
 */
const STRUCTURE_VIEW: StageViewConfig = {
  runningText: '组装中',
  bodyKind: 'checklist',
  metrics: () => [],
  composeBars: () => [],
  checklist: (node) => structureChecklist(node),
  summary: (node) => {
    if (node.status === 'RUNNING') {
      return '正在组装…';
    }
    return node.stageSummary ?? (node.status === 'FAILED' ? (node.errorMsg ?? '') : '');
  },
  tabs: [
    { key: 'tree', label: '结构树' },
    { key: 'elements', label: '元素' },
    { key: 'warnings', label: '告警' },
    { key: 'steps', label: '过程' },
  ],
  statItems: (detail) => {
    const summary = detail !== null && 'summary' in detail ? detail.summary : null;
    const outline = detail !== null && 'outline' in detail ? (detail.outline ?? []) : [];
    const stats = detail?.stageStats ?? null;
    const statNum = (key: string): number | null =>
      statNumber(typeof stats?.[key] === 'number' ? stats[key] : null);
    const titles = outline.filter((item) => item.type === 'TITLE');
    const level1 = titles.filter((item) => (item.level ?? 1) <= 1).length;
    const level2 = titles.filter((item) => item.level === 2).length;
    const level3 = titles.filter((item) => (item.level ?? 1) >= 3).length;
    return [
      { key: 'elements', label: '元素', value: countText(statNum('elementCount')) },
      { key: 'titles', label: '标题', value: countText(statNum('titleCount')) },
      {
        key: 'levels',
        label: '层级分布',
        value: titles.length > 0 ? `${level1} / ${level2} / ${level3}` : '—',
      },
      { key: 'tables', label: '表格', value: countText(summary?.tableCount) },
      { key: 'images', label: '图片', value: countText(summary?.imageCount) },
      { key: 'continuation', label: '疑似续表', value: countText(statNum('continuationCount')) },
      { key: 'conflicts', label: '冲突', value: countText(summary?.conflictCount) },
      { key: 'warnings', label: '告警', value: countText(summary?.warningCount) },
      { key: 'traced', label: '溯源覆盖率', value: coverageText(statNum('provenanceCoverage')) },
      { key: 'duration', label: '耗时', value: durationOf(detail, statNum('durationMs')) },
    ];
  },
  leftPaneKind: 'assembly',
};

/** 溯源覆盖率文案（后端已算成百分比整数） */
function coverageText(value: number | null): string {
  return value === null ? '—' : `${value}%`;
}

/**
 * 体检阈值（前端判定，与后端小结口径同源：后端也用 80% 这一档）。
 *
 * - 溯源覆盖率 < 80% → 黄；缺数据 → 灰
 * - 章节层数 < 2（或没有标题）→ 黄：结构太平，下游切片拿不到层级
 * - 疑似续表 > 0 → 黄（已按放宽规则接续，需人工确认）
 * - 重复段 > 0 或 噪声页 > 0 → 黄（处置在预处理环节）
 * - 无坐标元素 > 0 → 黄：这部分只能按原序追加，阅读顺序不保证
 */
const PROVENANCE_WARN_PERCENT = 80;
const MIN_REASONABLE_DEPTH = 2;

/**
 * 组装体检清单：每行 = 状态点 + 项目名 + 关键量 + 说明。
 *
 * <p>取值一律走 {@link statOf} 的数字兜底：产物不可读时统计为 null，各行落到"灰 + `—`"，
 * 不冒充真实值。
 */
function structureChecklist(node: LineageNode): StageCheckItem[] {
  const depth = statOf(node, 'chapterCount');
  const titles = statOf(node, 'titleCount');
  const total = statOf(node, 'elementCount');
  const coverage = statOf(node, 'provenanceCoverage');
  const traced = statOf(node, 'tracedCount');
  const continuation = statOf(node, 'continuationCount');
  const fromPage = statOf(node, 'continuationFromPage');
  const toPage = statOf(node, 'continuationToPage');
  const repeated = statOf(node, 'repeatedSegmentCount');
  const noise = statOf(node, 'noisePageCount');
  const withoutBbox = statOf(node, 'withoutBboxCount');

  const depthTone: CheckTone =
    depth === null || titles === null
      ? 'idle'
      : depth >= MIN_REASONABLE_DEPTH && titles > 0
        ? 'ok'
        : 'warn';
  const coverageTone: CheckTone =
    coverage === null ? 'idle' : coverage >= PROVENANCE_WARN_PERCENT ? 'ok' : 'warn';
  const continuationTone: CheckTone =
    continuation === null ? 'idle' : continuation > 0 ? 'warn' : 'ok';
  const repeatTone: CheckTone =
    repeated === null || noise === null ? 'idle' : repeated > 0 || noise > 0 ? 'warn' : 'ok';
  const orderTone: CheckTone = withoutBbox === null ? 'idle' : withoutBbox > 0 ? 'warn' : 'ok';

  return [
    {
      key: 'tree',
      label: '章节树',
      value: depth === null ? '—' : `${depth} 层`,
      note: titles === null ? '' : `${countText(titles)} 标题`,
      tone: depthTone,
    },
    {
      key: 'provenance',
      label: '溯源覆盖',
      value: coverageText(coverage),
      note: traced === null || total === null ? '' : `${countText(traced)}/${countText(total)}`,
      tone: coverageTone,
    },
    {
      key: 'continuation',
      label: '疑似续表',
      value: continuation === null ? '—' : `${countText(continuation)} 处`,
      note: continuationRangeText(continuation, fromPage, toPage),
      tone: continuationTone,
    },
    {
      key: 'repeat',
      label: '重复噪声',
      value:
        repeated === null && noise === null
          ? '—'
          : `${countText(repeated ?? 0)} 段·${countText(noise ?? 0)} 页`,
      note: '',
      tone: repeatTone,
    },
    {
      key: 'order',
      label: '阅读顺序',
      value:
        withoutBbox === null
          ? '—'
          : withoutBbox > 0
            ? `无坐标 ${countText(withoutBbox)}`
            : '已重排',
      note: withoutBbox !== null && withoutBbox > 0 ? '按原序追加' : '',
      tone: orderTone,
    },
  ];
}

/** 疑似续表页码范围：一条都没有时不给说明 */
function continuationRangeText(
  continuation: number | null,
  fromPage: number | null,
  toPage: number | null,
): string {
  if (continuation === null || continuation === 0 || fromPage === null || toPage === null) {
    return '';
  }
  return fromPage === toPage ? `第 ${fromPage} 页` : `第 ${fromPage}–${toPage} 页`;
}

/**
 * 耗时文案：优先用统计里现算的耗时，统计缺失时才按任务起止时间兜底。
 *
 * <p>组装产物的统计里就有 durationMs（读取侧现算），不必依赖时间字段。
 */
function durationOf(
  detail: ParseDetail | StructureDetail | null,
  statDurationMs: number | null,
): string {
  return statDurationMs === null ? durationText(detail) : formatDuration(statDurationMs);
}

/** 耗时按起止时间现算（组装产物里没有 durationMs 字段） */
function durationText(detail: ParseDetail | StructureDetail | null): string {
  if (detail?.startedAt === null || detail?.finishedAt === null || detail === null) {
    return '—';
  }
  const start = Date.parse(detail.startedAt);
  const end = Date.parse(detail.finishedAt);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
    return '—';
  }
  return formatDuration(end - start);
}

/** 未配置环节：中性内容（`—` 与空槽，不给任何环节专属文案） */
const NEUTRAL_VIEW: StageViewConfig = {
  runningText: '处理中',
  bodyKind: 'bars',
  checklist: () => [],
  metrics: () =>
    metricRow([
      { key: 'a', value: '—', unit: '' },
      { key: 'b', value: '—', unit: '' },
      { key: 'c', value: '—', unit: '' },
    ]),
  composeBars: () => emptyBars(),
  summary: () => '',
  tabs: [
    { key: 'elements', label: '元素' },
    { key: 'warnings', label: '告警' },
    { key: 'steps', label: '过程' },
  ],
  statItems: () => [],
  leftPaneKind: 'none',
};

/** 环节展示配置表：未列出的环节走 {@link NEUTRAL_VIEW} */
const STAGE_VIEWS: Record<string, StageViewConfig> = {
  PARSE: PARSE_VIEW,
  STRUCTURE: STRUCTURE_VIEW,
};

/** 取环节展示配置；未配置的环节返回中性配置 */
export function stageViewOf(stage: string | null | undefined): StageViewConfig {
  return STAGE_VIEWS[stage ?? ''] ?? NEUTRAL_VIEW;
}

/** 该环节是否已配置展示口径 */
export function isStageConfigured(stage: string | null | undefined): boolean {
  return Boolean(STAGE_VIEWS[stage ?? '']);
}
