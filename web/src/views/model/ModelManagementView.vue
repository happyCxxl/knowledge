<template>
  <div class="page">
    <div class="page-head">
      <!-- 标题与说明要包一层：.page-head 是 flex 行，不包会与右侧按钮并排 -->
      <div>
        <h1 class="page-title">模型管理</h1>
        <p class="page-desc">
          维护可用模型与价格限额；路由按容量占用、健康与延迟自动选型，参数在配置文件
        </p>
      </div>
      <div class="page-actions">
        <el-button class="page-btn-primary" @click="openCreate"> 新增模型 </el-button>
      </div>
    </div>

    <!-- 二级视图：模型清单是主入口，用量与成本是只读视图；两个视图都不涉及密钥 -->
    <div class="model-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        class="model-tab"
        :class="{ 'model-tab-active': activeTab === tab.key }"
        type="button"
        @click="handleTabChange(tab.key)"
      >
        {{ tab.label }}
        <span v-if="tab.key === 'models'" class="model-tab-count">{{ models.length }}</span>
      </button>
    </div>

    <!-- ========== 模型清单（主入口） ========== -->
    <div v-if="activeTab === 'models'" class="page-panel">
      <div class="page-toolbar">
        <el-select v-model="capabilityFilter" class="model-select" placeholder="能力">
          <el-option label="全部能力" value="" />
          <el-option
            v-for="item in MODEL_CAPABILITIES"
            :key="item"
            :label="MODEL_CAPABILITY_LABELS[item]"
            :value="item"
          />
        </el-select>
        <el-select v-model="statusFilter" class="model-select" placeholder="状态">
          <el-option label="全部状态" value="" />
          <el-option label="启用" :value="MODEL_STATUS.ENABLED" />
          <el-option label="停用" :value="MODEL_STATUS.DISABLED" />
        </el-select>
        <el-input
          v-model="keyword"
          class="model-search"
          placeholder="搜索模型名 / 展示名"
          clearable
          @keyup.enter="loadModels"
          @clear="loadModels"
        />
        <el-button class="page-btn-primary" @click="loadModels">查询</el-button>
        <el-button class="page-btn-ghost" @click="handleReset">重置</el-button>
        <button
          class="page-refresh"
          type="button"
          title="刷新"
          :disabled="loading"
          @click="loadModels"
        >
          <svg
            :class="{ 'page-refresh-spin': loading }"
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
      </div>

      <!-- 固定高度滚动区：高度由祖先链的确定高度决定，不随记录数变化 -->
      <div class="page-table-body">
        <!-- 列宽与对齐口径见 styles/page-shell.css 的「列表表格」一节：8 列同 min-width 即等宽 -->
        <el-table
          v-loading="loading"
          class="page-table"
          height="100%"
          :data="models"
          :border="false"
        >
          <el-table-column label="厂商" min-width="150" align="center">
            <template #default="{ row }">
              <div>{{ row.vendorName }}</div>
              <div class="model-muted model-mono">{{ row.vendorCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="150" align="center">
            <template #default="{ row }">
              <div>{{ row.displayName }}</div>
              <div class="model-muted model-mono">{{ row.modelKey }}</div>
            </template>
          </el-table-column>
          <el-table-column label="能力" min-width="150" align="center">
            <template #default="{ row }">
              <span v-for="item in row.capabilities" :key="item" class="model-tag">{{ item }}</span>
            </template>
          </el-table-column>
          <el-table-column label="价格" min-width="150" align="center">
            <template #default="{ row }">
              <span class="model-mono">{{ priceText(row as ModelItem) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="限额（RPM / TPM）" min-width="150" align="center">
            <template #default="{ row }">
              <span class="model-mono">{{ limitText(row as ModelItem) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="150" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === MODEL_STATUS.ENABLED ? 'success' : 'info'"
                effect="dark"
                size="small"
              >
                {{ row.status === MODEL_STATUS.ENABLED ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="150" align="center" fixed="right">
            <!-- 插槽里的 row 是 el-table 的 DefaultRow，交给强类型函数时逐个断言成 ModelItem -->
            <template #default="{ row }">
              <el-button
                class="model-row-btn"
                link
                type="primary"
                @click="openEdit(row as ModelItem)"
              >
                编辑
              </el-button>
              <el-button
                class="model-row-btn"
                link
                :type="row.status === MODEL_STATUS.ENABLED ? 'danger' : 'success'"
                :loading="togglingId === row.id"
                @click="handleToggleStatus(row as ModelItem)"
              >
                {{ row.status === MODEL_STATUS.ENABLED ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="page-table-empty">{{ loading ? '加载中…' : '暂无模型' }}</div>
          </template>
        </el-table>
      </div>

      <div class="page-panel-foot">
        <span>共 {{ models.length }} 条</span>
      </div>
    </div>

    <!-- ========== 用量与成本 ========== -->
    <div v-else class="page-panel">
      <div class="page-toolbar">
        <el-date-picker
          v-model="usageRange"
          class="model-range"
          type="daterange"
          value-format="YYYY-MM-DD"
          :clearable="false"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="loadUsage"
        />
        <el-select v-model="usageCapability" class="model-select" placeholder="能力">
          <el-option label="全部能力" value="" />
          <el-option
            v-for="item in MODEL_CAPABILITIES"
            :key="item"
            :label="MODEL_CAPABILITY_LABELS[item]"
            :value="item"
          />
        </el-select>
        <el-select v-model="usageRouteReason" class="model-select" placeholder="路由原因">
          <el-option label="全部原因" value="" />
          <el-option
            v-for="item in ROUTE_REASONS"
            :key="item"
            :label="ROUTE_REASON_LABELS[item]"
            :value="item"
          />
        </el-select>
        <el-button class="page-btn-primary" @click="loadUsage">查询</el-button>
        <button
          class="page-refresh"
          type="button"
          title="刷新"
          :disabled="usageLoading"
          @click="loadUsage"
        >
          <svg
            :class="{ 'page-refresh-spin': usageLoading }"
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
      </div>

      <div class="model-usage-body">
        <div class="model-stats">
          <div class="model-stat">
            <span class="model-stat-k">调用总数</span>
            <span class="model-stat-v">{{ summary.calls ?? '—' }}</span>
            <span class="model-stat-s">
              成功 {{ summary.successCalls ?? 0 }} · 失败 {{ summary.failedCalls ?? 0 }}
            </span>
          </div>
          <div class="model-stat">
            <span class="model-stat-k">已知成本合计</span>
            <span class="model-stat-v">{{ moneyText(summary.costTotal) }}</span>
            <span class="model-stat-s">未知用量行不参与合计</span>
          </div>
          <div class="model-stat">
            <span class="model-stat-k">未知用量占比</span>
            <span class="model-stat-v">{{ ratioText(summary.unknownRatio) }}</span>
            <span class="model-stat-s"
              >未知 {{ summary.unknownCalls ?? 0 }} 条 · 三项 token 与成本记 NULL</span
            >
          </div>
          <div class="model-stat">
            <span class="model-stat-k">切换次数（按 route_reason）</span>
            <span class="model-stat-v">{{ switchCount }}</span>
            <span class="model-stat-s">{{ switchSummaryText }}</span>
          </div>
        </div>

        <p class="model-usage-note">
          明细一次调用一行；`usage_unknown = 1` 的行三项 token 与成本记 NULL（未知），写 0
          表示确实为零，两者不混用。
        </p>

        <!-- 列宽口径同上：9 列同 min-width 即等宽 -->
        <el-table
          v-loading="usageLoading"
          class="page-table model-usage-table"
          height="360"
          :data="calls"
          :border="false"
        >
          <el-table-column label="时间" min-width="120" align="center">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="业务" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-muted">{{ row.bizType || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="能力" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-tag">{{ row.capability || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-mono">{{ row.model || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用量（入 / 出）" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-mono" :class="{ 'model-muted': row.usageUnknown === 1 }">
                {{ usageText(row as ModelCallLog) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="成本" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-mono" :class="{ 'model-muted': row.cost === null }">
                {{ row.cost === null ? 'NULL' : moneyText(row.cost) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="延迟" min-width="120" align="center">
            <template #default="{ row }">
              <span class="model-mono">{{
                row.latencyMs === null ? '—' : `${row.latencyMs}ms`
              }}</span>
            </template>
          </el-table-column>
          <el-table-column label="路由原因" min-width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.routeReason" class="model-tag">{{ row.routeReason }}</span>
              <span v-else class="model-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="120" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === CALL_STATUS.SUCCESS ? 'success' : 'danger'"
                effect="dark"
                size="small"
                :title="row.errorCode || ''"
              >
                {{ CALL_STATUS_LABELS[row.status] || row.status || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <div class="page-table-empty">{{ usageLoading ? '加载中…' : '暂无调用明细' }}</div>
          </template>
        </el-table>
      </div>
    </div>

    <!-- 新增 / 编辑：同一抽屉；厂商选项来自前端枚举，抽屉里没有密钥与地址字段 -->
    <el-drawer
      v-model="drawerVisible"
      class="model-drawer"
      :title="drawerTitle"
      size="520px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        :hide-required-asterisk="true"
      >
        <el-form-item prop="vendorCode" label="厂商" class="model-form-item">
          <el-select v-model="form.vendorCode" class="model-form-control" placeholder="请选择厂商">
            <el-option
              v-for="vendor in MODEL_VENDORS"
              :key="vendor.code"
              :label="`${vendor.displayName}（${vendor.code}）`"
              :value="vendor.code"
            />
          </el-select>
          <p class="model-hint">
            候选来自前端枚举，需与后端配置 knowledge.model.vendors 的键保持一致。
          </p>
        </el-form-item>

        <div class="model-form-pair">
          <el-form-item prop="modelKey" label="模型名（厂商侧）" class="model-form-item">
            <el-input
              v-model="form.modelKey"
              class="model-form-control"
              placeholder="如 qwen-max"
            />
          </el-form-item>
          <el-form-item prop="displayName" label="展示名" class="model-form-item">
            <el-input
              v-model="form.displayName"
              class="model-form-control"
              placeholder="留空取模型名"
            />
          </el-form-item>
        </div>

        <el-form-item prop="capabilities" label="能力" class="model-form-item">
          <el-select
            v-model="form.capabilities"
            class="model-form-control"
            multiple
            placeholder="至少选一项"
          >
            <el-option
              v-for="item in MODEL_CAPABILITIES"
              :key="item"
              :label="`${item} · ${MODEL_CAPABILITY_LABELS[item]}`"
              :value="item"
            />
          </el-select>
          <p class="model-hint">
            一个模型可勾多个能力，它会同时进入这些能力的候选集；冷却、占用与限额按模型算。
          </p>
        </el-form-item>

        <div class="model-form-pair">
          <el-form-item prop="contextWindow" label="上下文窗口（tokens）" class="model-form-item">
            <el-input-number
              v-model="form.contextWindow"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="可留空"
            />
          </el-form-item>
          <el-form-item prop="currency" label="币种" class="model-form-item">
            <el-select v-model="form.currency" class="model-form-control">
              <el-option v-for="item in MODEL_CURRENCIES" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </div>

        <div class="model-form-section">价格（按能力的计价口径填对应项，可留空）</div>
        <div class="model-form-pair">
          <el-form-item prop="priceInPerM" label="输入（/ 百万 token）" class="model-form-item">
            <el-input-number
              v-model="form.priceInPerM"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="留空表示未登记"
            />
          </el-form-item>
          <el-form-item prop="priceOutPerM" label="输出（/ 百万 token）" class="model-form-item">
            <el-input-number
              v-model="form.priceOutPerM"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="留空表示未登记"
            />
          </el-form-item>
        </div>
        <div class="model-form-pair">
          <el-form-item
            prop="priceImagePerPage"
            label="图像 / 版面 / OCR（/ 页）"
            class="model-form-item"
          >
            <el-input-number
              v-model="form.priceImagePerPage"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="留空表示不按页计价"
            />
          </el-form-item>
          <el-form-item prop="pricePerThousandCalls" label="嵌入（/ 千次）" class="model-form-item">
            <el-input-number
              v-model="form.pricePerThousandCalls"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="留空表示不按千次计价"
            />
          </el-form-item>
        </div>

        <div class="model-form-section">限额（都能留空；两项都留空则该模型不参与容量占用判断）</div>
        <div class="model-form-pair">
          <el-form-item prop="rpmLimit" label="RPM" class="model-form-item">
            <el-input-number
              v-model="form.rpmLimit"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="可留空"
            />
          </el-form-item>
          <el-form-item prop="tpmLimit" label="TPM" class="model-form-item">
            <el-input-number
              v-model="form.tpmLimit"
              class="model-form-control"
              :min="0"
              :controls="false"
              placeholder="可留空"
            />
          </el-form-item>
        </div>

        <div class="model-form-section">生效区间（留空 = 立即生效 / 长期有效）</div>
        <div class="model-form-pair">
          <el-form-item prop="effectiveFrom" label="开始时间" class="model-form-item">
            <el-date-picker
              v-model="form.effectiveFrom"
              class="model-form-control"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空 = 立即生效"
            />
          </el-form-item>
          <el-form-item prop="effectiveTo" label="结束时间" class="model-form-item">
            <el-date-picker
              v-model="form.effectiveTo"
              class="model-form-control"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="留空 = 长期有效"
            />
          </el-form-item>
        </div>

        <el-form-item prop="status" label="状态" class="model-form-item">
          <el-radio-group v-model="form.status">
            <el-radio :value="MODEL_STATUS.ENABLED">启用</el-radio>
            <el-radio :value="MODEL_STATUS.DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item prop="remark" label="备注" class="model-form-item">
          <el-input v-model="form.remark" class="model-form-control" placeholder="选填" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button class="page-btn-ghost" @click="drawerVisible = false">取消</el-button>
        <el-button class="page-btn-primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  addModel,
  getModelList,
  getModelUsageCalls,
  getModelUsageSummary,
  updateModel,
  updateModelStatus,
} from '@/api/model';
import {
  CALL_STATUS,
  CALL_STATUS_LABELS,
  MODEL_CAPABILITIES,
  MODEL_CAPABILITY_LABELS,
  MODEL_CURRENCIES,
  MODEL_STATUS,
  MODEL_VENDORS,
  ROUTE_REASON_LABELS,
  ROUTE_REASONS,
  type ModelCallLog,
  type ModelCallLogSummary,
  type ModelItem,
  type ModelSavePayload,
} from '@/types/model';
import { formatDateTime } from '@/utils/date';

// 模型管理页：模型清单（新增 / 编辑 / 启停）+ 用量与成本（概览与明细）；接口层已统一处理失败提示

/** 二级视图：模型清单与用量成本 */
const TABS = [
  { key: 'models', label: '模型清单' },
  { key: 'usage', label: '用量与成本' },
] as const;

type TabKey = (typeof TABS)[number]['key'];

/** 路由原因里表示常规选型的取值：其余取值都算一次切换 */
const ROUTE_REASON_NORMAL = 'OK';

/** 明细查询条数上限（后端未传 500、上限 2000）：切换次数按这一批明细统计 */
const USAGE_LIMIT = 500;

const activeTab = ref<TabKey>('models');

const capabilityFilter = ref('');
const statusFilter = ref('');
const keyword = ref('');
const models = ref<ModelItem[]>([]);
const loading = ref(false);
const togglingId = ref<string | null>(null);

const drawerVisible = ref(false);
const submitting = ref(false);
const editingId = ref<string | null>(null);
const formRef = ref<FormInstance>();

const usageLoading = ref(false);
const usageRange = ref<string[] | null>([todayText(), todayText()]);
const usageCapability = ref('');
const usageRouteReason = ref('');
const summary = ref<ModelCallLogSummary>(emptySummary());
const calls = ref<ModelCallLog[]>([]);

/** 抽屉表单的值形状：数值列可留空（null），生效区间两段可留空 */
interface ModelForm {
  vendorCode: string;
  modelKey: string;
  displayName: string;
  capabilities: string[];
  contextWindow: number | null;
  priceInPerM: number | null;
  priceOutPerM: number | null;
  priceImagePerPage: number | null;
  pricePerThousandCalls: number | null;
  currency: string;
  rpmLimit: number | null;
  tpmLimit: number | null;
  status: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  remark: string;
}

const form = reactive<ModelForm>({
  vendorCode: '',
  modelKey: '',
  displayName: '',
  capabilities: [],
  contextWindow: null,
  priceInPerM: null,
  priceOutPerM: null,
  priceImagePerPage: null,
  pricePerThousandCalls: null,
  currency: 'CNY',
  rpmLimit: null,
  tpmLimit: null,
  status: MODEL_STATUS.ENABLED,
  effectiveFrom: null,
  effectiveTo: null,
  remark: '',
});

/** 非负校验规则：价格与限额四类数值都不接受负数 */
const NON_NEGATIVE_RULE = {
  type: 'number' as const,
  min: 0,
  message: '不能为负数',
  trigger: 'change' as const,
};

const formRules: FormRules = {
  vendorCode: [{ required: true, message: '请选择厂商', trigger: 'change' }],
  modelKey: [
    { required: true, message: '请输入模型名', trigger: 'blur' },
    { max: 128, message: '模型名最长 128 字符', trigger: 'blur' },
  ],
  displayName: [{ max: 128, message: '展示名最长 128 字符', trigger: 'blur' }],
  capabilities: [
    { type: 'array', required: true, message: '至少选择一项能力', trigger: 'change' },
    {
      validator: (_rule, value: string[], callback: (error?: Error) => void) => {
        callback(value.length === 0 ? new Error('至少选择一项能力') : undefined);
      },
      trigger: 'change',
    },
  ],
  contextWindow: [NON_NEGATIVE_RULE],
  priceInPerM: [NON_NEGATIVE_RULE],
  priceOutPerM: [NON_NEGATIVE_RULE],
  priceImagePerPage: [NON_NEGATIVE_RULE],
  pricePerThousandCalls: [NON_NEGATIVE_RULE],
  rpmLimit: [NON_NEGATIVE_RULE],
  tpmLimit: [NON_NEGATIVE_RULE],
  remark: [{ max: 512, message: '备注最长 512 字符', trigger: 'blur' }],
  effectiveTo: [
    {
      validator: (_rule, value: string | null, callback: (error?: Error) => void) => {
        callback(endsBeforeStart(value) ? new Error('结束时间必须晚于开始时间') : undefined);
      },
      trigger: 'change',
    },
  ],
};

const drawerTitle = computed(() =>
  editingId.value === null ? '新增模型' : `编辑模型 · ${form.modelKey}`,
);

/** 明细里的路由原因分布（不含常规选型那一档），按条数降序 */
const routeReasonStats = computed(() => {
  const counts = new Map<string, number>();
  for (const item of calls.value) {
    const reason = item.routeReason;
    if (reason !== null && reason !== ROUTE_REASON_NORMAL) {
      counts.set(reason, (counts.get(reason) ?? 0) + 1);
    }
  }
  return [...counts.entries()].sort((left, right) => right[1] - left[1]);
});

const switchCount = computed(() => routeReasonStats.value.reduce((sum, item) => sum + item[1], 0));

const switchSummaryText = computed(() => {
  const stats = routeReasonStats.value;
  if (stats.length === 0) {
    return `最近 ${calls.value.length} 条明细里没有切换`;
  }
  return stats.map(([reason, count]) => `${reason} ${count}`).join(' · ');
});

/** 空概览：范围内没有调用时聚合列为 null，与「全部已知」区分 */
function emptySummary(): ModelCallLogSummary {
  return {
    calls: null,
    successCalls: null,
    failedCalls: null,
    timeoutCalls: null,
    rateLimitedCalls: null,
    unknownCalls: null,
    unknownRatio: null,
    totalTokens: null,
    costTotal: null,
  };
}

/** 当日日期文本（yyyy-MM-dd）：用量查询的默认区间两段都是今天 */
function todayText(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** 日期文本加一天（用量区间右端按不含计，取次日零点） */
function nextDayText(text: string): string {
  const date = new Date(`${text}T00:00:00`);
  date.setDate(date.getDate() + 1);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** 用量查询的时间两段（from 含 / to 不含）；区间读不到时按今天兜底 */
function usageTimeRange(): { from: string; to: string } {
  const range = usageRange.value;
  if (range === null || range.length < 2) {
    const today = todayText();
    return { from: today, to: nextDayText(today) };
  }
  return { from: range[0], to: nextDayText(range[1]) };
}

/** 金额展示：最多六位小数，去掉末尾的零；空值读作 — */
function moneyText(value: number | null): string {
  if (value === null) {
    return '—';
  }
  const text = Number(value).toFixed(6).replace(/0+$/, '').replace(/\.$/, '');
  return text === '' ? '0' : text;
}

/** 占比展示：后端下发四位小数（0.0081 → 0.81%），空值读作 — */
function ratioText(value: number | null): string {
  if (value === null) {
    return '—';
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}

/** 价格列文案：按能力取对应计价口径，多组之间用 / 连接 */
function priceText(item: ModelItem): string {
  const parts: string[] = [];
  if (item.capabilities.includes('CHAT')) {
    parts.push(
      `${moneyText(item.priceInPerM)} / ${moneyText(item.priceOutPerM)} ${item.currency}·百万 token`,
    );
  }
  if (item.capabilities.includes('EMBEDDING')) {
    parts.push(`${moneyText(item.pricePerThousandCalls)} ${item.currency}·千次`);
  }
  if (item.capabilities.some((capability) => capability !== 'CHAT' && capability !== 'EMBEDDING')) {
    parts.push(`${moneyText(item.priceImagePerPage)} ${item.currency}·页`);
  }
  return parts.length === 0 ? '—' : parts.join(' / ');
}

/** 限额列文案：两项都可留空，留空读作 — */
function limitText(item: ModelItem): string {
  const rpm = item.rpmLimit === null ? '—' : String(item.rpmLimit);
  const tpm = item.tpmLimit === null ? '—' : String(item.tpmLimit);
  return `${rpm} / ${tpm}`;
}

/** 用量列文案：用量标记为未知或两项 token 缺项时读 NULL，其余读「入 / 出」 */
function usageText(item: ModelCallLog): string {
  if (item.usageUnknown === 1 || item.promptTokens === null || item.completionTokens === null) {
    return 'NULL';
  }
  return `${item.promptTokens} / ${item.completionTokens}`;
}

/** 结束时间是否早于开始时间（两段都可留空，留空不比较） */
function endsBeforeStart(value: string | null): boolean {
  if (value === null || value === '' || form.effectiveFrom === null || form.effectiveFrom === '') {
    return false;
  }
  return value < form.effectiveFrom;
}

/** 非负兜底：表单控件已限最小值，提交前再扫一遍数值列 */
function firstNegativeField(): string | null {
  const values: [string, number | null][] = [
    ['上下文窗口', form.contextWindow],
    ['输入单价', form.priceInPerM],
    ['输出单价', form.priceOutPerM],
    ['按页单价', form.priceImagePerPage],
    ['按千次单价', form.pricePerThousandCalls],
    ['RPM 上限', form.rpmLimit],
    ['TPM 上限', form.tpmLimit],
  ];
  for (const [label, value] of values) {
    if (value !== null && value < 0) {
      return `${label}不能为负数`;
    }
  }
  return null;
}

/** 文本列：空白按留空下发（后端把留空按对应口径处理） */
function textOrNull(value: string): string | null {
  const text = value.trim();
  return text === '' ? null : text;
}

async function loadModels(): Promise<void> {
  loading.value = true;
  try {
    models.value = await getModelList({
      capability: capabilityFilter.value === '' ? undefined : capabilityFilter.value,
      status: statusFilter.value === '' ? undefined : statusFilter.value,
      keyword: keyword.value.trim() === '' ? undefined : keyword.value.trim(),
    });
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    loading.value = false;
  }
}

async function loadUsage(): Promise<void> {
  usageLoading.value = true;
  const { from, to } = usageTimeRange();
  const query = {
    from,
    to,
    capability: usageCapability.value === '' ? undefined : usageCapability.value,
    routeReason: usageRouteReason.value === '' ? undefined : usageRouteReason.value,
    limit: USAGE_LIMIT,
  };
  try {
    summary.value = await getModelUsageSummary(query);
    calls.value = await getModelUsageCalls(query);
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    usageLoading.value = false;
  }
}

/** 切到用量视图时取一次数（模型清单在进页面时已取过） */
function handleTabChange(key: TabKey): void {
  activeTab.value = key;
  if (key === 'usage' && calls.value.length === 0) {
    void loadUsage();
  }
}

/** 重置全部筛选条件并重新查询 */
function handleReset(): void {
  capabilityFilter.value = '';
  statusFilter.value = '';
  keyword.value = '';
  void loadModels();
}

/** 新增时默认选中枚举里的第一个厂商 */
function defaultVendorCode(): string {
  return MODEL_VENDORS[0].code;
}

/** 表单回到新增口径的初值 */
function resetForm(): void {
  form.vendorCode = defaultVendorCode();
  form.modelKey = '';
  form.displayName = '';
  form.capabilities = [];
  form.contextWindow = null;
  form.priceInPerM = null;
  form.priceOutPerM = null;
  form.priceImagePerPage = null;
  form.pricePerThousandCalls = null;
  form.currency = MODEL_CURRENCIES[0];
  form.rpmLimit = null;
  form.tpmLimit = null;
  form.status = MODEL_STATUS.ENABLED;
  form.effectiveFrom = null;
  form.effectiveTo = null;
  form.remark = '';
}

function openCreate(): void {
  editingId.value = null;
  resetForm();
  drawerVisible.value = true;
}

/** 编辑：把行上的值铺进表单，厂商也可改（仍只能选配置里已有的厂商） */
function openEdit(item: ModelItem): void {
  editingId.value = item.id;
  form.vendorCode = item.vendorCode;
  form.modelKey = item.modelKey;
  form.displayName = item.displayName;
  form.capabilities = [...item.capabilities];
  form.contextWindow = item.contextWindow;
  form.priceInPerM = item.priceInPerM;
  form.priceOutPerM = item.priceOutPerM;
  form.priceImagePerPage = item.priceImagePerPage;
  form.pricePerThousandCalls = item.pricePerThousandCalls;
  form.currency = item.currency;
  form.rpmLimit = item.rpmLimit;
  form.tpmLimit = item.tpmLimit;
  form.status = item.status;
  form.effectiveFrom = item.effectiveFrom;
  form.effectiveTo = item.effectiveTo;
  form.remark = item.remark ?? '';
  drawerVisible.value = true;
}

/** 表单 → 请求体：文本列空白按 null 下发，价格与限额留空即写空 */
function buildPayload(): ModelSavePayload {
  return {
    vendorCode: form.vendorCode,
    modelKey: form.modelKey.trim(),
    displayName: textOrNull(form.displayName),
    capabilities: [...form.capabilities],
    contextWindow: form.contextWindow,
    priceInPerM: form.priceInPerM,
    priceOutPerM: form.priceOutPerM,
    priceImagePerPage: form.priceImagePerPage,
    pricePerThousandCalls: form.pricePerThousandCalls,
    currency: form.currency,
    rpmLimit: form.rpmLimit,
    tpmLimit: form.tpmLimit,
    status: form.status,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo,
    remark: textOrNull(form.remark),
  };
}

async function handleSubmit(): Promise<void> {
  const formEl = formRef.value;
  if (formEl === undefined) {
    return;
  }
  const valid = await formEl.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const negative = firstNegativeField();
  if (negative !== null) {
    ElMessage.error(negative);
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await addModel(buildPayload());
      ElMessage.success('已新增模型');
    } else {
      await updateModel(editingId.value, buildPayload());
      ElMessage.success('已保存模型');
    }
    drawerVisible.value = false;
    await loadModels();
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    submitting.value = false;
  }
}

/** 启停：目标状态取反，成功后刷新清单 */
async function handleToggleStatus(item: ModelItem): Promise<void> {
  const enabled = item.status === MODEL_STATUS.ENABLED;
  togglingId.value = item.id;
  try {
    await updateModelStatus(item.id, enabled ? MODEL_STATUS.DISABLED : MODEL_STATUS.ENABLED);
    ElMessage.success(enabled ? '已停用模型' : '已启用模型');
    await loadModels();
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    togglingId.value = null;
  }
}

onMounted(async () => {
  await loadModels();
});
</script>

<style scoped lang="css">
/*
 * 页面骨架用全局 styles/page-shell.css 的 .page / .page-head / .page-title / .page-desc /
 * .page-panel / .page-toolbar / .page-table-body / .page-panel-foot；本页只补二级视图切换、
 * 用量概览卡与抽屉表单的私有类。
 */

/* ==================== 二级视图切换 ==================== */
.model-tabs {
  display: flex;
  flex: none;
  gap: 4px;
  align-items: center;
  border-bottom: 1px solid var(--kb-line);
}

.model-tab {
  padding: 9px 14px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--kb-text-2);
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  font-size: 13px;
  cursor: pointer;
  transition:
    color 0.18s,
    border-color 0.18s;
}

.model-tab:hover {
  color: var(--kb-text-1);
}

.model-tab-active {
  border-bottom-color: var(--kb-primary);
  color: var(--kb-text-1);
}

.model-tab-count {
  margin-left: 6px;
  color: var(--kb-text-3);
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

/* ==================== 工具条控件宽度 ==================== */
.model-select {
  width: 150px;
}

.model-search {
  width: 200px;
}

.model-range {
  width: 260px;
}

/* ==================== 表格内的文本样式 ==================== */
.model-muted {
  color: var(--kb-text-3);
  font-size: 12px;
}

.model-mono {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

/* 能力与路由原因标签：描边小标签，多个标签之间留一点间距 */
.model-tag {
  display: inline-block;
  margin: 1px 3px;
  padding: 1px 7px;
  border: 1px solid rgb(52 211 153 / 35%);
  border-radius: 6px;
  background: var(--kb-tint);
  color: var(--kb-primary);
  font-size: 11px;
}

.model-row-btn {
  padding: 0 5px;
  font-size: 12px;
}

/* ==================== 用量与成本 ==================== */
.model-usage-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.model-stats {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.model-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--kb-line);
  border-radius: 12px;
  background: rgb(255 255 255 / 3%);
}

.model-stat-k {
  color: var(--kb-text-2);
  font-size: 12px;
}

.model-stat-v {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
  font-size: 20px;
  font-weight: 650;
}

.model-stat-s {
  color: var(--kb-text-3);
  font-size: 11px;
  line-height: 1.6;
}

.model-usage-note {
  margin: 14px 0 10px;
  color: var(--kb-text-3);
  font-size: 12px;
}

.model-usage-table {
  border: 1px solid var(--kb-line);
  border-radius: 10px;
}

/* ==================== 抽屉表单 ==================== */
.model-hint {
  margin: 0 0 10px;
  color: var(--kb-text-3);
  font-size: 11px;
  line-height: 1.7;
}

.model-form-item {
  margin-bottom: 14px;
}

/* 控件撑满表单项：抽屉宽度固定，逐项单列铺开 */
.model-form-control {
  width: 100%;
}

.model-form-pair {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

/* 分组小标题：价格、限额与生效区间三段各一处 */
.model-form-section {
  margin: 6px 0 10px;
  color: var(--kb-text-2);
  font-size: 12px;
}
</style>
