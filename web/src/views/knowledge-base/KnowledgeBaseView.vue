<template>
  <div class="kb-page">
    <div class="kb-page-head">
      <div class="kb-page-title-wrap">
        <h1 class="kb-page-title">知识库</h1>
        <p class="kb-page-desc">管理你的知识库与文档资产，查看构建与运行状态</p>
      </div>
      <div class="kb-actions">
        <el-button class="kb-btn-ghost" plain @click="handlePending">导入文档</el-button>
        <el-button class="kb-btn-primary" @click="openCreate">新建知识库</el-button>
      </div>
    </div>
    <div class="kb-stats">
      <div v-for="item in statItems" :key="item.label" class="kb-stat">
        <span class="kb-stat-num">{{ item.value }}</span>
        <span class="kb-stat-label">{{ item.label }}</span>
      </div>
    </div>
    <div class="kb-panel">
      <div class="kb-filter-bar">
        <el-input
          v-model="keyword"
          class="kb-search"
          placeholder="搜索知识库名称"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <div class="kb-filter-group">
          <span class="kb-filter-label">状态</span>
          <button
            v-for="filter in statusFilters"
            :key="filter.label"
            class="kb-chip"
            :class="{ 'kb-chip-on': selectedStatus === filter.value }"
            type="button"
            @click="handleFilter(filter.value)"
          >
            {{ filter.label }}
          </button>
        </div>
        <el-select v-model="sortBy" class="kb-sort-select" @change="handleSort">
          <el-option label="默认（默认库优先）" value="DEFAULT" />
          <el-option label="最近更新" value="UPDATED" />
          <el-option label="名称" value="NAME" />
        </el-select>
        <el-button class="kb-btn-ghost" @click="handleSearch">查询</el-button>
        <el-button class="kb-btn-ghost" @click="handleClearFilter">清除筛选</el-button>
        <button class="kb-refresh" type="button" title="刷新" :disabled="loading" @click="refresh">
          <svg
            class="kb-refresh-icon"
            :class="{ 'kb-refresh-spin': loading }"
            width="14"
            height="14"
            viewBox="0 0 16 16"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <path d="M13.4 8a5.4 5.4 0 1 1-1.6-3.8M13.4 1.9v2.4H11" />
          </svg>
        </button>
        <span class="kb-filter-count">共 {{ total }} 个</span>
      </div>
      <div v-if="kbList.length > 0" class="kb-grid">
        <KnowledgeBaseCard
          v-for="item in kbList"
          :key="item.id"
          :kb="item"
          @update="openEdit"
          @evaluate="handlePending"
          @delete="handleDelete"
        />
        <button class="kb-new" type="button" @click="openCreate">
          <span class="kb-new-plus">+</span>
          新建知识库
        </button>
      </div>
      <div v-else-if="loading" class="kb-empty">加载中…</div>
      <div v-else-if="hasFilter" class="kb-empty">
        <p class="kb-empty-text">没有符合当前筛选条件的知识库</p>
        <el-button class="kb-btn-ghost" @click="handleClearFilter">清除筛选</el-button>
      </div>
      <div v-else class="kb-empty">
        <p class="kb-empty-text">还没有知识库，先创建一个吧</p>
        <el-button class="kb-btn-primary" @click="openCreate">新建知识库</el-button>
      </div>
      <div class="kb-panel-foot">
        <span>共 {{ total }} 个知识库</span>
        <div class="kb-panel-foot-right">
          <el-select v-model="query.size" class="kb-size-select" @change="handleSizeChange">
            <el-option :value="12" label="12 条/页" />
            <el-option :value="24" label="24 条/页" />
            <el-option :value="48" label="48 条/页" />
          </el-select>
          <el-pagination
            v-model:current-page="query.current"
            class="kb-pager"
            layout="prev, pager, next"
            :total="total"
            :page-size="query.size"
            @current-change="loadList"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      class="kb-dialog"
      :title="isEdit ? '编辑知识库' : '新建知识库'"
      width="440px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="dialogFormRef"
        :model="dialogForm"
        :rules="dialogRules"
        label-position="top"
        :hide-required-asterisk="true"
      >
        <el-form-item prop="name" label="知识库名称" class="kb-dialog-item">
          <el-input v-model="dialogForm.name" placeholder="如：金融研报库（≤128 字符）" />
        </el-form-item>
        <el-form-item prop="description" label="业务场景说明" class="kb-dialog-item">
          <el-input
            v-model="dialogForm.description"
            type="textarea"
            :rows="3"
            placeholder="如：金融行业 · 研究报告与公告（≤512 字符）"
          />
        </el-form-item>
        <el-form-item prop="strategyBindingEnabled" label="策略绑定" class="kb-dialog-item">
          <el-radio-group v-model="dialogForm.strategyBindingEnabled">
            <el-radio :value="1">开启（触发走本库绑定策略）</el-radio>
            <el-radio :value="0">关闭（测评模式，触发须显式选策略）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="kb-btn-ghost" @click="dialogVisible = false">取消</el-button>
        <el-button class="kb-btn-primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  addKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseDetail,
  getKnowledgeBasePage,
  getKnowledgeBaseStats,
  updateKnowledgeBase,
} from '@/api/knowledge-base';
import KnowledgeBaseCard from '@/components/knowledge-base/KnowledgeBaseCard.vue';
import { KB_STATUS_ACTIVE, KB_STATUS_DISABLED } from '@/types/knowledge-base';
import type { KnowledgeBase, KnowledgeBaseSort } from '@/types/knowledge-base';

// 知识库页：统计概览与知识库卡片列表
type StatusFilter = number | 'all';

const route = useRoute();
const router = useRouter();

const statusFilters: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: KB_STATUS_ACTIVE, label: '已启用' },
  { value: KB_STATUS_DISABLED, label: '已停用' },
];

const kbList = ref<KnowledgeBase[]>([]);
const total = ref(0);
const loading = ref(false);
const selectedStatus = ref<StatusFilter>('all');
const keyword = ref('');
const sortBy = ref<KnowledgeBaseSort>('DEFAULT');
const query = ref({ current: 1, size: 12 });

/** 是否存在生效中的筛选条件：用于区分「筛选无匹配」与「确实还没有知识库」 */
const hasFilter = computed(() => keyword.value.trim() !== '' || selectedStatus.value !== 'all');

// 统计条：知识库总数与文档总数（文档数按 kb_file_result 记录数，即提交任务数）
const stats = ref({ knowledgeBaseCount: '0', documentCount: '0' });
const statItems = computed(() => [
  { value: stats.value.knowledgeBaseCount, label: '知识库' },
  { value: stats.value.documentCount, label: '文档数' },
]);

async function loadList(): Promise<void> {
  loading.value = true;
  try {
    const page = await getKnowledgeBasePage({
      current: query.value.current,
      size: query.value.size,
      name: keyword.value.trim() || undefined,
      status: selectedStatus.value === 'all' ? undefined : selectedStatus.value,
      sort: sortBy.value,
    });
    kbList.value = page.records;
    total.value = page.total;
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    loading.value = false;
  }
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await getKnowledgeBaseStats();
  } catch {
    // 失败提示已由接口层统一拦截处理
  }
}

/** 查询条件变化后回到第一页，避免停留在越界页码 */
function handleSearch(): void {
  query.value.current = 1;
  void loadList();
}

/** 切换状态过滤后重新查询 */
function handleFilter(value: StatusFilter): void {
  selectedStatus.value = value;
  handleSearch();
}

/** 切换排序口径后重新查询 */
function handleSort(): void {
  handleSearch();
}

/** 每页条数变化后回到第一页 */
function handleSizeChange(): void {
  handleSearch();
}

/** 清空全部筛选条件并重新查询 */
function handleClearFilter(): void {
  keyword.value = '';
  selectedStatus.value = 'all';
  sortBy.value = 'DEFAULT';
  handleSearch();
}

/** 刷新列表与统计 */
function refresh(): void {
  void loadList();
  void loadStats();
}

// ---------------- 新建 / 编辑 ----------------

const dialogVisible = ref(false);
const submitting = ref(false);
const editingId = ref<string | null>(null);
const dialogFormRef = ref<FormInstance>();

const dialogForm = reactive({
  name: '',
  description: '',
  strategyBindingEnabled: 1,
});

const isEdit = computed(() => editingId.value !== null);

const dialogRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 128, message: '名称最长 128 字符', trigger: 'blur' },
  ],
  description: [{ max: 512, message: '业务场景说明最长 512 字符', trigger: 'blur' }],
};

function openCreate(): void {
  editingId.value = null;
  Object.assign(dialogForm, { name: '', description: '', strategyBindingEnabled: 1 });
  dialogVisible.value = true;
}

async function openEdit(kb: KnowledgeBase): Promise<void> {
  editingId.value = kb.id;
  Object.assign(dialogForm, {
    name: kb.name,
    description: kb.description ?? '',
    // 列表已带该字段，先用快照填表避免弹窗空一下
    strategyBindingEnabled: kb.strategyBindingEnabled ?? 1,
  });
  dialogVisible.value = true;
  try {
    // 再取一次详情：列表快照可能已被他人改动，避免用陈旧值覆盖
    const detail = await getKnowledgeBaseDetail(kb.id);
    Object.assign(dialogForm, {
      name: detail.name,
      description: detail.description ?? '',
      strategyBindingEnabled: detail.strategyBindingEnabled ?? 1,
    });
  } catch {
    // 详情取失败时保留列表快照；失败提示已由接口层统一处理
  }
}

async function handleSubmit(): Promise<void> {
  const valid = await dialogFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      name: dialogForm.name.trim(),
      description: dialogForm.description.trim() || undefined,
      strategyBindingEnabled: dialogForm.strategyBindingEnabled,
    };
    if (isEdit.value) {
      const id = editingId.value ?? '';
      await updateKnowledgeBase(id, { ...payload, id });
      ElMessage.success('保存成功');
    } else {
      await addKnowledgeBase(payload);
      ElMessage.success('创建成功');
    }
    dialogVisible.value = false;
    // 新建后回到第一页：列表按 id 倒序，新库在第一页
    if (!isEdit.value) {
      query.value.current = 1;
    }
    void loadList();
    void loadStats();
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    submitting.value = false;
  }
}

/** 删除知识库：二次确认 → 逻辑删除 → 刷新；默认库卡片不渲染删除入口 */
async function handleDelete(kb: KnowledgeBase): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确认删除知识库「${kb.name}」？删除后该库及其下的文档任务将不可见，且无法恢复`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  ).catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await deleteKnowledgeBase(kb.id);
    ElMessage.success('删除成功');
    // 删掉当前页最后一条时回退一页，避免停留在空列表（必须在重载前调页）
    if (kbList.value.length === 1 && query.value.current > 1) {
      query.value.current -= 1;
    }
    void loadList();
    void loadStats();
  } catch {
    // 失败提示已由接口层统一拦截处理
  }
}

function handleDialogClosed(): void {
  dialogFormRef.value?.clearValidate();
  editingId.value = null;
}

// 待接入后端接口的功能统一提示入口
function handlePending(): void {
  ElMessage.info('该功能待接入后端接口');
}

onMounted(() => {
  void loadList();
  void loadStats();
  applyEntryAction();
});

/**
 * 响应首页动作卡带来的入口参数（?action=create / ?action=import）。
 *
 * <p>首页的动作卡只是跳到这里，真正的操作在本页完成，避免首页重复实现一遍导入/新建。
 * 处理完立刻把参数从地址栏抹掉，否则刷新会再次触发。
 */
function applyEntryAction(): void {
  const action = route.query.action;
  if (action !== 'create' && action !== 'import') {
    return;
  }
  if (action === 'create') {
    openCreate();
  } else {
    ElMessage.info('请选择要导入文档的知识库');
  }
  void router.replace({ path: '/knowledge-base' });
}
</script>

<style scoped lang="css">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 18px;

  /* 固定高度：等于「视口 - 顶栏(62px) - 内容区上下内边距(26+40)」，不随卡片数量变化 */
  height: calc(100vh - 128px);
  min-height: 420px;
}

.kb-page-head {
  display: flex;
  flex: none;
  align-items: flex-end;
  justify-content: space-between;
}

.kb-page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}

.kb-page-desc {
  margin: 6px 0 0;
  color: var(--kb-text-3);
  font-size: 13px;
}

.kb-actions {
  display: flex;
  gap: 10px;
}

.kb-btn-ghost {
  border-color: var(--kb-line-strong);
  background: rgb(255 255 255 / 4%);
  color: var(--kb-text-1);
}

.kb-btn-primary {
  border: none;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 6px 22px rgb(52 211 153 / 25%);
  color: var(--kb-btn-text);
}

.kb-btn-primary:hover,
.kb-btn-primary:focus {
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 8px 28px var(--kb-glow);
  color: var(--kb-btn-text);
  filter: brightness(1.08);
}

.kb-stats {
  display: flex;
  flex: none;
  align-items: baseline;
  padding: 6px 2px;
}

.kb-stat {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 0 30px;
}

.kb-stat:first-child {
  padding-left: 0;
}

.kb-stat + .kb-stat {
  border-left: 1px solid var(--kb-line);
}

.kb-stat-num {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 24px;
  font-weight: 650;
}

.kb-stat-label {
  color: var(--kb-text-3);
  font-size: 13px;
}

.kb-panel {
  display: flex;

  /* 占满剩余高度；min-height:0 让内部滚动区能正确收缩而不撑破容器 */
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--kb-line);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 3%), rgb(255 255 255 / 1.2%));
}

/* 筛选栏：搜索 + 状态芯片 + 排序 + 查询/清除 + 刷新 + 计数，同属面板顶部一行 */
.kb-filter-bar {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-line);
}

.kb-search {
  width: 200px;
}

.kb-filter-group {
  display: flex;
  gap: 6px;
  align-items: center;
}

.kb-filter-label {
  color: var(--kb-text-3);
  font-size: 12px;
}

.kb-sort-select {
  width: 170px;
}

.kb-size-select {
  width: 104px;
}

.kb-filter-count {
  margin-left: auto;
  color: var(--kb-text-3);
  font-size: 12px;
}

.kb-chip {
  padding: 5px 12px;
  border: 1px solid var(--kb-line);
  border-radius: 99px;
  background: none;
  color: var(--kb-text-2);
  font-size: 12px;
  cursor: pointer;
  transition:
    border-color 0.18s,
    background 0.18s,
    color 0.18s;
}

.kb-chip:hover {
  border-color: var(--kb-line-strong);
  color: var(--kb-text-1);
}

.kb-chip-on {
  border-color: rgb(52 211 153 / 40%);
  background: var(--kb-tint);
  color: var(--kb-primary);
}

.kb-refresh {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(255 255 255 / 3%);
  color: var(--kb-text-2);
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s;
}

.kb-refresh:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/* 刷新中：禁用并旋转图标，让「点了没反应」变成可见反馈 */
.kb-refresh:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.kb-refresh:disabled:hover {
  border-color: var(--kb-line);
  color: var(--kb-text-2);
}

.kb-refresh-spin {
  animation: kb-spin 0.9s linear infinite;
}

@keyframes kb-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 卡片滚动区：flex:1 + min-height:0 拿到确定高度，卡片多时在面板内部滚动 */
.kb-grid {
  display: grid;
  flex: 1;
  gap: 14px;
  align-content: start;
  min-height: 0;
  overflow-y: auto;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  padding: 18px;
}

.kb-new {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  border: 1px dashed var(--kb-line-strong);
  border-radius: var(--kb-radius);
  background: transparent;
  color: var(--kb-text-3);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    color 0.2s;
}

.kb-new:hover {
  border-color: rgb(52 211 153 / 50%);
  background: var(--kb-tint);
  color: var(--kb-primary);
}

.kb-new-plus {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px dashed rgb(52 211 153 / 50%);
  border-radius: 50%;
  color: var(--kb-primary);
  font-size: 19px;
}

.kb-empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: var(--kb-text-3);
  font-size: 13px;
  text-align: center;
}

.kb-empty-text {
  margin: 0;
}

.kb-panel-foot {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  padding: 13px 18px;
  border-top: 1px solid var(--kb-line);
  color: var(--kb-text-3);
  font-size: 13px;
}

.kb-panel-foot-right {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
