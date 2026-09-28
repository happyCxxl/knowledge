<template>
  <div class="home">
    <!-- 欢迎 + 轻量入口：三个入口不再占大块版面 -->
    <div class="home-hd">
      <h1 class="home-hi">你好，{{ username }}</h1>
      <span class="home-spacer"></span>
    </div>

    <!-- 资产：放最上面，一行带；细分区给"现在能用什么"，而不是库存总数 -->
    <div class="home-strip">
      <div class="home-cell">
        <span class="home-cell-k">知识库</span>
        <span class="home-cell-v">{{ summaryText.knowledgeBaseCount }}</span>
        <span class="home-cell-sub">启用 {{ summaryText.enabledKnowledgeBaseCount }}</span>
      </div>
      <div class="home-cell">
        <span class="home-cell-k">文档</span>
        <span class="home-cell-v">{{ summaryText.documentCount }}</span>
        <span class="home-cell-sub">提交总数</span>
      </div>
      <div class="home-cell">
        <span class="home-cell-k">可用策略</span>
        <span class="home-cell-v">{{ summaryText.strategyVersionCount }}</span>
        <span class="home-cell-sub">
          预处理 {{ summaryText.preprocessVersionCount }} · 切片
          {{ summaryText.chunkVersionCount }} · 向量化 {{ summaryText.embedVersionCount }} · 检索
          {{ summaryText.retrievalVersionCount }}
        </span>
      </div>
    </div>

    <!-- 最近提交：文件提交（日常操作），与下面的管理动作互补 -->
    <div class="home-sec">
      <span class="home-sec-title">最近提交</span>
      <span class="home-spacer"></span>
      <el-pagination
        v-model:current-page="submitQuery.current"
        class="home-pager"
        layout="prev, pager, next"
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
        <el-table-column header-align="center" label="时间" :width="COL.time">
          <template #default="{ row }">
            <span class="home-mono home-dim">{{ formatActivityTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="文件名" :min-width="COL.fileName">
          <template #default="{ row }">
            <span class="home-file home-ellipsis" :title="row.fileName">{{
              row.fileName || '（文件名不可得）'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="知识库" :min-width="COL.kbName">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.knowledgeBaseName">{{
              row.knowledgeBaseName
            }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="提交人" :min-width="COL.operator">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.operator">{{
              row.operator ?? '—'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="结果" :min-width="COL.result">
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
      <span class="home-spacer"></span>
      <el-pagination
        v-model:current-page="activityQuery.current"
        class="home-pager"
        layout="prev, pager, next"
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
        <el-table-column header-align="center" label="时间" :width="COL.time">
          <template #default="{ row }">
            <span class="home-mono home-dim">{{ formatActivityTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="操作人" :min-width="COL.operator">
          <template #default="{ row }">
            <span class="home-dim home-ellipsis" :title="row.operator">{{
              row.operator ?? '—'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column header-align="center" label="动作" :min-width="COL.action">
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
        <el-table-column header-align="center" label="对象" :min-width="COL.object">
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
        <el-table-column header-align="center" label="变更" :min-width="COL.delta">
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
 * 两张列表的固定高度（px）= 表头 40 + 5 行 × 42 = 250。
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
 * <p>分配依据是各列内容长度：时间固定 11 个等宽字符；
 * 提交人是用户名、知识库名、动作是短标签；文件名 / 对象名 / 变更长度不可控，给更大权重。
 * 只有 `time` 用固定 `width`（内容长度恒定，不希望它随窗口变宽）。
 */
const COL = {
  /** 时间：yyyy-MM-dd HH:mm 的短格式，固定宽 */
  time: 112,
  /** 提交人 / 操作人：用户名，放得下 zz-import-verify */
  operator: 160,
  /** 动作：四字标签 + 徽标内边距 */
  action: 150,
  /** 文件名：长度不可控，给大权重 */
  fileName: 320,
  /** 知识库名 */
  kbName: 200,
  /** 结果：通过 / 未通过 徽标 + 失败原因 */
  result: 220,
  /** 对象名：可能拼成「库名 版本号」 */
  object: 280,
  /** 变更：字段清单可能很长，给最大权重 */
  delta: 360,
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
.home {
  display: flex;
  flex-direction: column;
}

.home-spacer {
  flex: 1;
}

/* ==================== 头部 ==================== */
.home-hd {
  display: flex;
  gap: 7px;
  align-items: center;
  margin-bottom: 20px;
}

.home-hi {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

/* ==================== 资产带 ==================== */
.home-strip {
  display: grid;
  gap: 0;
  margin-bottom: 26px;
  border: 1px solid var(--kb-line);
  border-radius: 11px;
  background: linear-gradient(180deg, rgb(255 255 255 / 3.5%), rgb(255 255 255 / 1%));
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.home-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 13px 16px;
  border-right: 1px solid var(--kb-line);
}

.home-cell:last-child {
  border-right: none;
}

.home-cell-k {
  color: var(--kb-text-3);
  font-size: 11px;
}

.home-cell-v {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 21px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
}

.home-cell-sub {
  color: var(--kb-text-4);
  font-size: 11px;
  line-height: 1.5;
}

/* ==================== 区块标题 ==================== */
.home-sec {
  display: flex;
  gap: 9px;
  align-items: center;
  margin-bottom: 11px;
}

/* 第二块与第一块列表拉开距离 */
.home-sec-gap {
  margin-top: 26px;
}

.home-sec-title {
  font-size: 13px;
  font-weight: 650;
}

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
