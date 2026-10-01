<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">你好，{{ username }}</h1>
        <p class="page-desc">平台资产概览，以及我最近提交的文件</p>
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
        <!-- 副文案刻意不写"提交总数"：校验失败的提交不建结果，两个数不是一回事 -->
        <span class="page-asset-sub">已建档文档</span>
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

    <!--
      最近提交：**只含本人**的记录（后端按登录用户过滤，前端不传提交人），
      所以不再需要"提交人"列。面板走共用骨架：标题在 .page-toolbar，计数与翻页在 .page-panel-foot。
    -->
    <div class="page-panel">
      <div class="page-toolbar">
        <span class="home-panel-title">最近提交</span>
        <span class="page-spacer"></span>
      </div>

      <!--
        表体 flex:1 + min-height:0：高度由面板决定，**与本页记录数无关** ——
        末页只有两三条时容器也保持同高，翻页不会跳。窗口太矮时是表格内部滚动。
        加载态用 v-loading 遮罩，不能用 v-if 换掉表格（翻页会闪）；空态走 #empty 插槽。
      -->
      <div class="home-table-body">
        <el-table
          v-loading="submitLoading"
          class="home-table"
          :data="submits"
          :border="false"
          height="100%"
        >
          <el-table-column label="文件名" :min-width="COL.fileName">
            <template #default="{ row }">
              <span class="home-file home-ellipsis" :title="row.fileName">{{
                row.fileName || '（文件名不可得）'
              }}</span>
            </template>
          </el-table-column>
          <el-table-column label="文件类型" :width="COL.fileType">
            <template #default="{ row }">
              <!-- 类型来自来源文件的 MIME（不按扩展名猜）；校验失败的行取不到，显示占位符 -->
              <span class="home-mono home-dim">{{ row.fileType ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="知识库" :min-width="COL.kbName">
            <template #default="{ row }">
              <span class="home-dim home-ellipsis" :title="row.knowledgeBaseName">{{
                row.knowledgeBaseName
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
          <el-table-column label="提交时间" :width="COL.createTime">
            <template #default="{ row }">
              <span class="home-mono home-dim">{{ formatSubmitTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <div class="home-empty">{{ submitLoading ? '加载中…' : '还没有提交记录' }}</div>
          </template>
        </el-table>
      </div>

      <div class="page-panel-foot">
        <span>共 {{ submitTotal }} 条</span>
        <!-- 页码固定显示：首页是唯一的提交历史入口，精确跳页有实际价值 -->
        <el-pagination
          v-model:current-page="submitQuery.current"
          class="home-pager"
          layout="prev, pager, next"
          :total="submitTotal"
          :page-size="PAGE_SIZE"
          :disabled="submitLoading"
          @current-change="loadSubmits"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统首页：**资产速览 + 最近提交（仅本人）**。
 *
 * <p>两块内容，回答两个问题：
 *
 * <ul>
 *   <li>**资产速览**（最上面）—— "现在有什么、能用什么"。细分区刻意给"启用数 / 四类可用策略"，
 *       而不是库存总数：索引版本那种数字在没用起来时恒为 0，列出来等于没信息，
 *       而且知识库卡片上已经有「索引版本」一格，首页再列是重复；</li>
 *   <li>**最近提交**（`kb_submit_log`）—— **当前登录用户**的文件提交。`status` 是
 *       **提交校验结果**（PASS/FAIL），**不是处理链进度**，所以工具栏里写明了这句。
 *       可见范围由后端按登录用户过滤，前端不传提交人，因此没有"提交人"列。</li>
 * </ul>
 *
 * <p>每页固定 {@link PAGE_SIZE} 条：列表容器高度由面板决定、与本页记录数无关，
 * 末页条数少时不会塌下去，翻页也不会跳。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { getHomeRecentSubmits, getHomeSummary } from '@/api/home';
import { useAuthStore } from '@/stores/auth';
import { formatSubmitTime } from '@/types/home';
import type { HomeRecentSubmit, HomeSummary } from '@/types/home';

const authStore = useAuthStore();

/** 固定每页条数（页长不随窗口高度变化，翻页用页码） */
const PAGE_SIZE = 10;

/**
 * 表格列宽（px）。
 *
 * <p>两类值分工明确：
 *
 * <ul>
 *   <li>**定长内容 → 固定 `width`**：文件类型是 3~4 个字符的枚举名（PDF / DOCX / XLSX），
 *       提交时间是 `yyyy-MM-dd HH:mm` 定长 16 字符 —— 让它们随窗口变宽只会多出留白；</li>
 *   <li>**长度不可控 → `min-width` 吃富余**：文件名可能来长名，交给 `.home-ellipsis` 截断；
 *       知识库名与结果（徽标 + 失败原因）按"刚好放得下"给最小值。</li>
 * </ul>
 *
 * <p>**表头对齐方式跟随单元格，不单独设 `header-align`**：Element Plus 不传时继承 `align`（左对齐），
 * 正是我们要的；单独写 `center` 会让列头浮在内容上方的中间，看起来"歪"。
 */
const COL = {
  /** 文件名：不可控，吃富余宽度，超长截断 */
  fileName: 260,
  /** 文件类型：`PDF` / `DOCX` / `XLSX` 等 3~4 字符，定宽 */
  fileType: 100,
  /** 知识库名：短且稳定（实测最长 65px + 内边距） */
  kbName: 140,
  /** 结果：通过/未通过徽标 + 失败原因（实测最长 147px + 内边距） */
  result: 200,
  /** 提交时间：`yyyy-MM-dd HH:mm` 定长（实测 68px→16 字符约 110px + 内边距） */
  createTime: 150,
} as const;

const username = computed(() => authStore.username ?? '');
const summary = ref<HomeSummary | null>(null);

/** 最近提交（仅本人） */
const submits = ref<HomeRecentSubmit[]>([]);
const submitLoading = ref(false);
const submitTotal = ref(0);
const submitQuery = reactive({ current: 1, size: PAGE_SIZE });

/**
 * 数字按千分位展示；未加载完显示占位符，避免闪 0。
 *
 * <p>只映射界面上真实展示的字段：后端就不再下发索引版本那两个计数了
 * （知识库卡片上已有「索引版本」一格，首页重复列出等于没信息）。
 */
const summaryText = computed(() => {
  const pick = (value: string | number | undefined): string => formatCount(value);
  return {
    knowledgeBaseCount: pick(summary.value?.knowledgeBaseCount),
    enabledKnowledgeBaseCount: pick(summary.value?.enabledKnowledgeBaseCount),
    strategyVersionCount: pick(summary.value?.strategyVersionCount),
    preprocessVersionCount: pick(summary.value?.preprocessVersionCount),
    chunkVersionCount: pick(summary.value?.chunkVersionCount),
    embedVersionCount: pick(summary.value?.embedVersionCount),
    retrievalVersionCount: pick(summary.value?.retrievalVersionCount),
    documentCount: pick(summary.value?.documentCount),
  };
});

function formatCount(value: string | number | undefined): string {
  if (value === undefined || value === '') {
    return '—';
  }
  // 计数在小数值区间（后端全局口径：只有超过 JS 安全整数的雪花 ID 才下发字符串），
  // 所以这里拿到的是 number；但保留对字符串的兼容（旧数据/未来口径变化都不至于显示成 —）
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

onMounted(() => {
  void loadSummary();
  void loadSubmits();
});
</script>

<style scoped lang="css">
/*
 * 页面骨架来自全局 styles/page-shell.css：
 *   .page-head / .page-title / .page-desc  头部
 *   .page-assets / .page-asset*            资产带（竖排 标签/数值/副文案）
 *   .page-panel / .page-toolbar / .page-panel-foot  内容面板
 * 本页只保留业务样式。
 */

/* 面板标题（放进共用的 .page-toolbar） */
.home-panel-title {
  font-size: 13px;
  font-weight: 650;
}

/*
 * 表体：flex:1 + min-height:0 拿到确定高度，el-table 据此固定表头并按内容滚动。
 * **高度与本页记录数无关**（只跟面板/窗口有关）：末页只有两三条时容器不会塌下去，
 * 翻页不会跳动；窗口太矮时是表格内部滚动，页面本身不被裁切。
 */
.home-table-body {
  flex: 1;
  min-height: 0;
}

/* 分页：总数在左侧那行文字里，这里只放页码与翻页按钮 */
.home-pager {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: transparent;
  --el-pagination-text-color: var(--kb-text-2);
  --el-pagination-button-color: var(--kb-text-2);
  --el-pagination-hover-color: var(--kb-primary);
}

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

  background: transparent;
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
 * 单行截断：表格行高必须恒定，否则最后一行会被容器切掉。
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

/* 失败原因：跟在结果徽标后面，弱化以免抢视觉 */
.home-reason {
  margin-left: 7px;
  color: var(--kb-text-4);
  font-size: 11px;
}

/* 结果徽标：通过 / 未通过 两档配色 */
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
</style>
