<template>
  <div class="page home">
    <div class="page-head">
      <div>
        <h1 class="page-title">你好，{{ username }}</h1>
        <p class="page-desc">平台资产概览，以及最近的文件提交与管理操作</p>
      </div>
    </div>

    <!--
      资产带：用共用的 .page-assets（竖排 标签/数值/副文案），而不是 .page-stats ——
      这里除了数值还有"细分说明"（可用策略要拆成预处理/切片/向量化/检索），
      与列表页顶部那种"就是一个数"的横排指标不是同一层级。
    -->
    <div class="page-assets">
      <div class="page-asset">
        <span class="page-asset-k">知识库</span>
        <span class="page-asset-v">{{ summaryText.knowledgeBaseCount }}</span>
        <span class="page-asset-sub">启用 {{ summaryText.enabledKnowledgeBaseCount }}</span>
      </div>
      <div class="page-asset">
        <span class="page-asset-k">文档</span>
        <span class="page-asset-v">{{ summaryText.documentCount }}</span>
        <span class="page-asset-sub">提交总数</span>
      </div>
      <div class="page-asset">
        <span class="page-asset-k">可用策略</span>
        <span class="page-asset-v">{{ summaryText.strategyVersionCount }}</span>
        <span class="page-asset-sub">
          预处理 {{ summaryText.preprocessVersionCount }} · 切片
          {{ summaryText.chunkVersionCount }} · 向量化 {{ summaryText.embedVersionCount }} · 检索
          {{ summaryText.retrievalVersionCount }}
        </span>
      </div>
    </div>

    <!-- 最近提交：文件提交（日常操作），与下面的管理动作互补 -->
    <div class="home-sec">
      <span class="home-sec-title">最近提交</span>
      <span class="page-spacer"></span>
      <el-pagination
        v-model:current-page="submitQuery.current"
        class="home-pager"
        layout="total, prev, next"
        :total="submitTotal"
        :page-size="submitQuery.size"
        :disabled="submitLoading"
        @current-change="loadSubmits"
      />
    </div>

    <!--
      同样用 v-loading 遮罩而非 v-if 换表格（翻页销毁重建会闪）；
      与行为记录表共用同一个固定高度（TABLE_HEIGHT），两张表页长也一致（TABLE_PAGE_SIZE）。
      列宽见 script 里的 COL 常量：五列按内容权重分配，不用「窄列 + 超宽列」的拼凑比例。
    -->
    <div class="home-table-body" :style="{ height: `${TABLE_HEIGHT}px` }">
      <el-table
        v-loading="submitLoading"
        class="home-panel home-table"
        :data="submits"
        :border="false"
        height="100%"
      >
        <el-table-column label="时间" :width="COL.time">
          <template #default="{ row }">
            <span class="home-mono home-dim">{{ formatActivityTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文件名" :min-width="COL.fileName">
          <template #default="{ row }">
            <span class="home-file home-ellipsis" :title="row.fileName">{{
              row.fileName || '（文件名不可得）'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="知识库" :min-width="COL.kbName">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.knowledgeBaseName">{{
              row.knowledgeBaseName
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交人" :min-width="COL.operator">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.operator">{{
              row.operator ?? '—'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" :min-width="COL.result">
          <template #default="{ row }">
            <span
              class="home-act"
              :class="{ 'is-ok': row.status === 'PASS', 'is-danger': row.status === 'FAIL' }"
            >
              {{ row.status === 'PASS' ? '通过' : '未通过' }}
            </span>
            <span v-if="row.failReasonLabel" class="home-reason">{{ row.failReasonLabel }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="home-empty">{{ submitLoading ? '加载中…' : '还没有提交记录' }}</div>
        </template>
      </el-table>
    </div>

    <!-- 行为记录：管理员动作（kb_audit_log），不含文件提交 -->
    <div class="home-sec home-sec-gap">
      <span class="home-sec-title">行为记录</span>
      <span class="page-spacer"></span>
      <el-pagination
        v-model:current-page="activityQuery.current"
        class="home-pager"
        layout="total, prev, next"
        :total="activityTotal"
        :page-size="activityQuery.size"
        :disabled="activityLoading"
        @current-change="loadActivities"
      />
    </div>

    <!--
      加载态用 v-loading 遮罩，**不能用 v-if 把表格换成"加载中…"**：
      那样每次翻页都会销毁并重建整张表格（连带 el-table 内部 DOM 与布局重算），页面会闪一下。
      空态走 #empty 插槽，表格始终挂载。
      与最近提交表共用同一个固定高度（TABLE_HEIGHT）与页长（TABLE_PAGE_SIZE）。
    -->
    <div class="home-table-body" :style="{ height: `${TABLE_HEIGHT}px` }">
      <el-table
        v-loading="activityLoading"
        class="home-panel home-table"
        :data="activities"
        :border="false"
        height="100%"
      >
        <!-- 时间列放得下 11 个等宽字符 + 单元格内边距；列宽见 script 的 COL 常量 -->
        <el-table-column label="时间" :width="COL.time">
          <template #default="{ row }">
            <span class="home-mono home-dim">{{ formatActivityTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" :min-width="COL.operator">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.operator">{{
              row.operator ?? '—'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="动作" :min-width="COL.action">
          <template #default="{ row }">
            <span
              class="home-act"
              :class="{
                'is-ok': activityTone(row.actionType) === 'ok',
                'is-danger': activityTone(row.actionType) === 'danger',
              }"
            >
              {{ row.actionLabel }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="对象" :min-width="COL.object">
          <template #default="{ row }">
            <!-- 名称 + 类型徽标紧贴排列（.home-obj 用 0 1 auto，不能用 1，否则徽标被推到列右缘）；全文放 title -->
            <span class="home-obj-line">
              <span class="home-obj home-ellipsis" :title="row.objectName">{{
                row.objectName
              }}</span>
              <span class="home-kind">{{ row.objectTypeLabel }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变更" :min-width="COL.delta">
          <template #default="{ row }">
            <!-- 历史数据里变更列存过整段 JSON，必须单行截断（全文放 title），否则行高被撑破容器 -->
            <span class="home-mono home-dim home-ellipsis" :title="deltaText(row)">{{
              deltaText(row) || '—'
            }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="home-empty">{{ activityLoading ? '加载中…' : '还没有管理操作记录' }}</div>
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统首页：**资产速览 + 最近提交 + 行为记录**。
 *
 * <p>三块内容，回答三个问题：
 *
 * <ul>
 *   <li>**资产速览**（最上面）—— "现在有什么、能用什么"。细分区刻意给"启用数 / 四类策略"，
 *       而不是库存总数：索引版本那种数字在没用起来时恒为 0，列出来等于没信息，
 *       而且知识库卡片上已经有「索引版本」一格，首页再列是重复；</li>
 *   <li>**最近提交**（`kb_submit_log`）—— 日常文件提交。`status` 是**提交校验结果**
 *       （PASS/FAIL），**不是处理链进度**，所以区块注明了"非处理进度"；</li>
 *   <li>**行为记录**（`kb_audit_log`）—— 管理员关键操作（发布/回退/绑定/建库改库/账号变动）。
 *       与上一块是两种口径，所以分成两个表，不混在一起。</li>
 * </ul>
 *
 * <p>两块列表都按接口的分页信息渲染分页器 —— 接口本来就分页返回，
 * 前端写死 `current: 1` 会让第二页之后的数据永远看不到。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { getHomeActivities, getHomeRecentSubmits, getHomeSummary } from '@/api/home';
import { useAuthStore } from '@/stores/auth';
import { activityTone, deltaText, formatActivityTime } from '@/types/home';
import type { HomeActivity, HomeActivityQuery, HomeRecentSubmit, HomeSummary } from '@/types/home';

const authStore = useAuthStore();

/** 表头高度（px），实测值；两张列表一致 */
const TABLE_HEAD_HEIGHT = 40;

/** 表数据行高度（px），实测值；两张列表一致，改单元格 padding 时要同步 */
const TABLE_ROW_HEIGHT = 42;

/**
 * 两张列表的每页条数。
 *
 * <p>**必须两张表一致**：它们共用同一个固定高度，页长不同会让一张表放得下、另一张被裁
 * （或反过来留大片空白），翻页时高度也会跳。
 */
const TABLE_PAGE_SIZE = 5;

/**
 * 两张列表的固定高度（px）= 表头 40 + 页长 × 42。
 *
 * <p>由 {@link TABLE_PAGE_SIZE} 与行高算出，**三者必须一起改**：
 * 高度小于「表头 + 页长 × 行高」会切出内部滚动条，大于则会多出一条空行。
 *
 * <p>固定而非随内容伸缩：末页条数少时容器也保持同高，
 * 分页器与页面下方内容不会因行数变化而跳动。
 */
const TABLE_HEIGHT = TABLE_HEAD_HEIGHT + TABLE_PAGE_SIZE * TABLE_ROW_HEIGHT;

/**
 * 表格列宽（px）。
 *
 * <p>两张表都用 `min-width` 为主：el-table 会把富余宽度**按 min-width 比例**分给各列，
 * 所以这些值同时决定「谁更宽」，不要给某列单独设很大的值（那会让它吃掉全部富余宽度）。
 *
 * <p>**权重按「内容的取值范围」定，不是按某一次采样量出来的宽度** —— 后者等于拿碰巧
 * 出现的那几行当设计依据，数据一变就又歪了。所以先查了各列的取值范围（SQL 的
 * MIN/MAX(CHAR_LENGTH)），再据此分配：
 *
 * <ul>
 *   <li>**短且稳定 → 收紧**：知识库名 5~11 字、动作 4~13 字、提交人 4~16 字。
 *       这些列给到「刚好放得下」即可；给大了会白拿富余宽度，表现为列与列之间莫名多出空隙
 *       （实测「动作」列内容只 71px 却被撑到 170px）；</li>
 *   <li>**长且不可控 → 吃剩余空间 + 省略号**：文件名 15~21 字但可能来长名、
 *       变更 2~301 字。它们天然该占剩下来的宽度，截断交给 `.home-ellipsis`。</li>
 * </ul>
 *
 * <p>只有 `time` 用固定 `width`：内容是 yyyy-MM-dd HH:mm 的定长格式，
 * 不希望它随窗口变宽（变宽只会让这一列的留白变多）。
 *
 * <p>**表头对齐方式跟随单元格，不要单独设 `header-align`**：单元格内容一律左对齐
 * （时间/文件名等文本天然左对齐），而表头原先写的是 `header-align="center"` ——
 * 于是列头文字浮在内容上方的中间位置，与内容不在同一条竖直线上，看起来"歪"。
 * Element Plus 的 `header-align` 不传时继承 `align`（默认 left），正是我们要的。
 */
const COL = {
  /** 时间：`MM-DD HH:mm` 定长，固定宽（实测最长 68px + 内边距 = 92） */
  time: 112,
  /** 提交人 / 操作人：用户名（实测最长 97px + 内边距 = 121） */
  operator: 125,
  /** 动作：四字标签徽标（实测最长 65px + 内边距 = 89） */
  action: 95,
  /** 文件名：不可控（实测最长 211px + 内边距 = 235），允许截断 */
  fileName: 240,
  /** 知识库名：短且稳定（实测最长 65px + 内边距 = 89） */
  kbName: 95,
  /** 结果：通过/未通过 徽标 + 失败原因（实测最长 147px + 内边距 = 171） */
  result: 175,
  /** 对象名：库名 + 版本号 + 类型徽标（实测最长 218px + 内边距 = 242） */
  object: 245,
  /**
   * 变更：**唯一需要吃富余的列**（实测最长内容 909px，表格总共才 1190px，必然截断）。
   *
   * <p>给它最大的权重是刻意的：其余列的最小值都按「刚好放得下」设（89~245px），
   * 富余宽度按比例分配时，权重越集中在变更列，其余列越不会被撑出大片空白 ——
   * 而空白正是「列间距看起来不均匀」的来源。
   */
  delta: 300,
} as const;

const username = computed(() => authStore.username ?? '');
const summary = ref<HomeSummary | null>(null);

/** 最近提交：一屏放得下，10 条一页 */
const submits = ref<HomeRecentSubmit[]>([]);
const submitLoading = ref(false);
const submitTotal = ref(0);
const submitQuery = reactive({ current: 1, size: TABLE_PAGE_SIZE });

/** 行为记录 */
const activities = ref<HomeActivity[]>([]);
const activityLoading = ref(false);
const activityTotal = ref(0);
const activityQuery = reactive<HomeActivityQuery>({ current: 1, size: TABLE_PAGE_SIZE });

/** 数字按千分位展示；未加载完显示占位符，避免闪 0 */
const summaryText = computed(() => {
  const pick = (value: string | undefined): string => formatCount(value);
  return {
    knowledgeBaseCount: pick(summary.value?.knowledgeBaseCount),
    enabledKnowledgeBaseCount: pick(summary.value?.enabledKnowledgeBaseCount),
    strategyVersionCount: pick(summary.value?.strategyVersionCount),
    preprocessVersionCount: pick(summary.value?.preprocessVersionCount),
    chunkVersionCount: pick(summary.value?.chunkVersionCount),
    embedVersionCount: pick(summary.value?.embedVersionCount),
    retrievalVersionCount: pick(summary.value?.retrievalVersionCount),
    indexVersionCount: pick(summary.value?.indexVersionCount),
    onlineIndexVersionCount: pick(summary.value?.onlineIndexVersionCount),
    documentCount: pick(summary.value?.documentCount),
  };
});

function formatCount(value: string | undefined): string {
  if (value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  return Number.isNaN(num) ? '—' : num.toLocaleString('en-US');
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await getHomeSummary();
  } catch {
    // 失败提示已由接口层统一拦截；这里保持占位符
  }
}

async function loadSubmits(): Promise<void> {
  submitLoading.value = true;
  try {
    const page = await getHomeRecentSubmits({
      current: submitQuery.current,
      size: submitQuery.size,
    });
    submits.value = page.records;
    submitTotal.value = page.total;
  } catch {
    submits.value = [];
    submitTotal.value = 0;
  } finally {
    submitLoading.value = false;
  }
}

async function loadActivities(): Promise<void> {
  activityLoading.value = true;
  try {
    const page = await getHomeActivities({
      current: activityQuery.current,
      size: activityQuery.size,
    });
    activities.value = page.records;
    activityTotal.value = page.total;
  } catch {
    activities.value = [];
    activityTotal.value = 0;
  } finally {
    activityLoading.value = false;
  }
}

onMounted(() => {
  void loadSummary();
  void loadSubmits();
  void loadActivities();
});
</script>

<style scoped lang="css">
/*
 * 页面骨架来自全局 styles/page-shell.css：
 *   .page-head / .page-title / .page-desc  头部
 *   .page-assets / .page-asset*            资产带（竖排 标签/数值/副文案）
 * 本页只保留业务样式。原先各写一套的 .home-hd / .home-hi / .home-strip / .home-cell*
 * 已删除 —— 那是"头部 19px + 自造资产带"，与列表页的 22px 头部不一致（实测过）。
 *
 * <p>**首页不套用 `.page` 的固定高度**（列表页才需要"一屏装下、内部滚动"）：
 * 它纵向内容天然比一屏长 —— 两张表各 5 行共 500px，加头部/资产带/间距合计约 795px，
 * 而桌面可用只有 777px。硬套固定高度会把两个 .home-table-body 用 flex 压缩
 * （250px → 212px），内部 el-table 装不下就冒出滚动条 —— 实测正是这么出现的。
 *
 * <p>改为自然高度后，**整页由 `.layout-content` 的 `overflow: hidden` 裁切** ——
 * 用户明确要求「整页不允许滚动」，所以只能把内容压进一屏：
 * 本页 gap 压到 10px（共用层 18px 对首页偏大）、资产带内边距压到 10px、
 * 区块标题与表格的间距压到 6px、块间额外间距压到 2px。
 * **这几项加起来是挤出约 30px 换回"每表 5 行"**，改动这里要重新核对一屏能否装下。
 */
.home {
  flex: none;
  height: auto;
  gap: 10px;
  min-height: 0;
}

/*
 * 资产带内边距收紧（14px → 10px）：首页纵向最紧，而它是纯展示块，
 * 收内边距不影响识别。只在本页覆盖，不动共用层的默认值。
 */
.home .page-asset {
  padding: 10px 18px;
}

/* ==================== 区块标题 ==================== */
.home-sec {
  display: flex;
  gap: 9px;
  align-items: center;

  /*
   * 标题与它下面那张表的距离，就是这里的 margin-bottom（用户要求"减少标题与表格的距离"）。
   * 从 11px 收到 6px：标题本身已经与表头有视觉区分（字号/颜色都不同），不需要那么多留白。
   */
  margin-bottom: 6px;
}

/*
 * 第二块（行为记录）与第一块表格之间的距离。
 *
 * <p>用户要求"减少最近提交与下方表格的距离" —— 原先是 26px 的额外 margin，
 * 叠在 .home 的 gap(12px) 之上共 38px，对两块并列内容过重。
 * 收到 2px（合计 14px）：块与块仍有区分，但不至于断开。
 */
.home-sec-gap {
  margin-top: 2px;
}

.home-sec-title {
  font-size: 13px;
  font-weight: 650;
}

/*
 * 分页：**只用「总数 + 上一页/下一页」，不放页码按钮**。
 *
 * <p>原先两处都是 `layout="prev, pager, next"`。El-Pagination 的 `pager-count` 默认 7 ——
 * 页数不超过 7 就**全部列出**、不做省略，于是两张表并排时长这样：
 *
 * <pre>
 *   最近提交  [1][2][3]                       宽 160px（12 行 / 页长 5 = 3 页）
 *   行为记录  [1][2][3][4][5][6][7]           宽 288px（33 行 / 页长 5 = 7 页）
 * </pre>
 *
 * <p>两处配置其实完全一致，宽度却差 128px，看起来像配置不一致；而且行为记录只增不减，
 * 页数一涨宽度还会继续跳。首页是**只看不改的概览区**，精确跳页的价值很低，
 * 宽度稳定更重要 —— 所以换成固定宽度的「总数 + 翻页」。
 */
.home-pager {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: transparent;
  --el-pagination-text-color: var(--kb-text-2);
  --el-pagination-button-color: var(--kb-text-2);
  --el-pagination-hover-color: var(--kb-primary);
}

/* ==================== 列表 ==================== */

/* el-table 主题化：与用户管理页同一套变量覆盖，保证两处表格观感一致 */
.home-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--kb-bg-1);
  --el-table-border-color: var(--kb-line);
  --el-table-text-color: var(--kb-text-1);
  --el-table-header-text-color: var(--kb-text-2);
  --el-table-row-hover-bg-color: rgb(52 211 153 / 6%);

  /* 翻页时的加载遮罩：默认是白色蒙层，在深色底上会白闪一下，改成极淡的暗色 */
  --el-loading-spinner-size: 26px;
  --el-mask-color: rgb(10 14 18 / 45%);

  overflow: hidden;
  border-radius: 11px;
}

/* 固定高度的外层：el-table 用 height="100%" 撑满，翻页时表格不重建、高度也不变 */
.home-table-body {
  /*
   * flex: none 不能省：高度由模板内联给出（TABLE_HEIGHT = 表头 + 页长 × 行高），
   * 但 flex 项默认 flex-shrink:1 —— 父容器变矮时**内联 height 会被压缩**，
   * 而内部的 el-table 装不下就冒出滚动条（实测被压到 212px 时正是这个现象）。
   */
  flex: none;
  overflow: hidden;
  border-radius: 11px;
}

.home-empty {
  padding: 26px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}

.home-mono {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 11px;
}

.home-dim {
  color: var(--kb-text-3);
}

/*
 * 单行截断：表格行高必须恒为 42px（固定高度 = 表头 + 页长 × 42 的算法依赖它）。
 * 历史数据里变更列存过整段 JSON，不截断会把行撑到 80px+，导致容器装不下最后一行、且出现滚动条。
 * 完整内容用 title 属性承载（见模板）。
 */
.home-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-file {
  color: var(--kb-text-1);
}

/* 对象列的「名称 + 类型徽标」一行：名称吃掉剩余宽度，min-width:0 才能让省略号生效 */
.home-obj-line {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}

/* 失败原因：跟在结果徽标后面，弱化以免抢视觉 */
.home-reason {
  margin-left: 7px;
  color: var(--kb-text-4);
  font-size: 11px;
}

/* 结果/动作徽标：只分「正向 / 破坏性 / 中性」三档，各配一色反而看不出重点 */
.home-act {
  display: inline-flex;
  align-items: center;
  height: 19px;
  padding: 0 8px;
  border: 1px solid var(--kb-line-2);
  border-radius: 5px;
  font-size: 11px;
  white-space: nowrap;
}

.home-act.is-ok {
  border-color: rgb(52 211 153 / 35%);
  background: rgb(52 211 153 / 10%);
  color: var(--kb-primary);
}

.home-act.is-danger {
  border-color: rgb(248 113 113 / 32%);
  background: rgb(248 113 113 / 9%);
  color: var(--kb-danger);
}

/*
 * 名称 + 徽标紧贴排列：不能用 flex:1 —— 那会把名称撑满整列、把徽标推到列右边缘，
 * 中间留下大片空白，看起来像两列。这里让名称按内容宽度排布，超长时才收缩并出省略号。
 */
.home-obj {
  flex: 0 1 auto;
  min-width: 0;
  color: var(--kb-text-1);
}

/* 类型徽标不参与压缩，否则会被省略号挤掉 */
.home-kind {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--kb-line-2);
  border-radius: 4px;
  color: var(--kb-text-4);
  font-size: 10px;
  white-space: nowrap;
}
</style>
