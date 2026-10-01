<template>
  <!-- 规则动作：三选一 -->
  <div v-for="key in PREPROCESS_ACTION_RULES" :key="key" class="sm-row">
    <span class="sm-row-label">
      {{ PREPROCESS_RULE_LABELS[key] }}
      <i class="sm-help" :title="PREPROCESS_RULE_HINTS[key]">?</i>
    </span>
    <el-radio-group
      v-if="!readonly"
      :model-value="actionOf(key)"
      size="small"
      @update:model-value="(value: string | number | boolean | undefined) => setAction(key, value)"
    >
      <el-radio-button v-for="action in ACTIONS" :key="action" :value="action">
        {{ PREPROCESS_ACTION_LABELS[action] }}
      </el-radio-button>
    </el-radio-group>
    <span v-else class="sm-row-value">{{ PREPROCESS_ACTION_LABELS[actionOf(key)] }}</span>
  </div>

  <!-- 目录识别阈值：仅在目录动作不是 KEEP 时才有意义 -->
  <div v-if="tocMarked" class="sm-params">
    <label v-for="key in PREPROCESS_TOC_PARAM_KEYS" :key="key" class="sm-param">
      <span class="sm-param-label">{{ PREPROCESS_TOC_PARAM_LABELS[key] }}</span>
      <el-input
        v-if="!readonly"
        :model-value="tocParamOf(key)"
        size="small"
        class="sm-param-input"
        @update:model-value="(value: string) => setTocParam(key, value)"
      />
      <span v-else class="sm-row-value">{{ tocParamOf(key) || '—' }}</span>
    </label>
  </div>

  <!-- 开关类规则 -->
  <div v-for="key in PREPROCESS_TOGGLE_RULES" :key="key" class="sm-row">
    <span class="sm-row-label">
      {{ PREPROCESS_RULE_LABELS[key] }}
      <i class="sm-help" :title="PREPROCESS_RULE_HINTS[key]">?</i>
    </span>
    <el-switch
      v-if="!readonly"
      :model-value="toggleOf(key)"
      size="small"
      @update:model-value="(value: string | number | boolean) => setToggle(key, value)"
    />
    <span v-else class="sm-row-value">{{ toggleOf(key) ? '开' : '关' }}</span>
  </div>

  <!-- 字段标准化的四类子开关：规则开着时才展示 -->
  <div v-if="toggleOf('field')" class="sm-sub">
    <div v-for="key in PREPROCESS_FIELD_KEYS" :key="key" class="sm-sub-row">
      <span class="sm-sub-label">{{ PREPROCESS_FIELD_LABELS[key] }}</span>
      <el-switch
        v-if="!readonly"
        :model-value="fieldParamOf(key)"
        size="small"
        @update:model-value="(value: string | number | boolean) => setFieldParam(key, value)"
      />
      <span v-else class="sm-row-value">{{ fieldParamOf(key) ? '开' : '关' }}</span>
    </div>
  </div>

  <!-- 文本整理的五个子开关 -->
  <div v-if="toggleOf('tidy')" class="sm-sub">
    <div v-for="key in PREPROCESS_TIDY_KEYS" :key="key" class="sm-sub-row">
      <span class="sm-sub-label">{{ PREPROCESS_TIDY_LABELS[key] }}</span>
      <el-switch
        v-if="!readonly"
        :model-value="tidyParamOf(key)"
        size="small"
        @update:model-value="(value: string | number | boolean) => setTidyParam(key, value)"
      />
      <span v-else class="sm-row-value">{{ tidyParamOf(key) ? '开' : '关' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 预处理策略表单。
 *
 * <p>同一个组件承担「只读查看」与「编辑」两种形态：`readonly` 为真时只渲染值，
 * 避免查看与编辑两套口径各写一遍而漂移。
 *
 * <p>**配置对象是就地改的**：只动自己负责的键，其余键原样保留。
 * 后端会在保存时做归一化与校验，前端丢掉不认识的键会抹掉后端写入的字段。
 */
import { computed } from 'vue';

import {
  PREPROCESS_ACTION_LABELS,
  PREPROCESS_ACTION_RULES,
  PREPROCESS_FIELD_KEYS,
  PREPROCESS_FIELD_LABELS,
  PREPROCESS_RULE_HINTS,
  PREPROCESS_RULE_LABELS,
  PREPROCESS_TIDY_KEYS,
  PREPROCESS_TIDY_LABELS,
  PREPROCESS_TOC_PARAM_KEYS,
  PREPROCESS_TOC_PARAM_LABELS,
  PREPROCESS_TOGGLE_RULES,
  preprocessAction,
  preprocessToggle,
} from '@/types/strategy-config';
import type {
  PreprocessAction,
  PreprocessConfig,
  PreprocessRuleRaw,
} from '@/types/strategy-config';

/**
 * 配置对象用 v-model 双向绑定：表单在编辑态会就地改它（新增/修改键），
 * 用普通 prop 会触发 vue/no-mutating-props，也可能悄悄改到只读视图用的那份。
 * 父组件传 v-model:config，并且只在编辑态挂载可改的表单。
 */
const config = defineModel<PreprocessConfig>('config', { required: true });

defineProps<{ readonly: boolean }>();

const ACTIONS: PreprocessAction[] = ['KEEP', 'MARK', 'EXCLUDE'];

/** 取规则对象，缺失时就地补一个，保证后续赋值有落点 */
function ruleOf(key: string): PreprocessRuleRaw {
  // 用「键是否存在」而不是「值是否真值」判断：后者在 TS 看来恒为真（索引签名非可选），
  // 而且值本身可能是空对象（合法配置），用真值判断既会被 lint 拦下也不够准确
  if (!Object.hasOwn(config.value.rules, key)) {
    config.value.rules[key] = {};
  }
  return config.value.rules[key];
}

function actionOf(key: string): PreprocessAction {
  return preprocessAction(config.value.rules[key]);
}

function setAction(key: string, value: string | number | boolean | undefined): void {
  ruleOf(key).action = String(value);
}

function toggleOf(key: string): boolean {
  return preprocessToggle(config.value.rules[key]);
}

function setToggle(key: string, value: string | number | boolean | undefined): void {
  ruleOf(key).enabled = value === true ? 'ON' : 'OFF';
}

/** 规则对象里的 params，缺失时就地补。返回的一定是对象，调用方无需再判空 */
function paramsOf(key: string): Record<string, string> {
  const rule = ruleOf(key);
  if (!rule.params) {
    rule.params = {};
  }
  return rule.params;
}

/** 只读一条规则：规则不存在时返回 null，不产生副作用（与 paramsOf 的就地补区分开） */
function existingParamsOf(key: string): Record<string, string> | null {
  if (!Object.hasOwn(config.value.rules, key)) {
    return null;
  }
  const rule = config.value.rules[key];
  return rule.params ?? null;
}

function tocParamOf(key: string): string {
  return existingParamsOf('toc')?.[key] ?? '';
}

function setTocParam(key: string, value: string): void {
  paramsOf('toc')[key] = value;
}

/** 子开关统一按 'ON' / 'OFF' 存字符串（与后端配置口径一致） */
function subParamOf(ruleKey: string, paramKey: string): boolean {
  return existingParamsOf(ruleKey)?.[paramKey] === 'ON';
}

function setSubParam(
  ruleKey: string,
  paramKey: string,
  value: string | number | boolean | undefined,
): void {
  paramsOf(ruleKey)[paramKey] = value === true ? 'ON' : 'OFF';
}

function fieldParamOf(key: string): boolean {
  return subParamOf('field', key);
}

function setFieldParam(key: string, value: string | number | boolean | undefined): void {
  setSubParam('field', key, value);
}

function tidyParamOf(key: string): boolean {
  return subParamOf('tidy', key);
}

function setTidyParam(key: string, value: string | number | boolean | undefined): void {
  setSubParam('tidy', key, value);
}

/** 目录被标记或剔除时，识别阈值才参与判定 */
const tocMarked = computed(() => actionOf('toc') !== 'KEEP');
</script>

<style scoped lang="css">
/* 表单行：左标签右控件；查看态与编辑态共用同一套排版 */
.sm-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  border-bottom: 1px solid var(--kb-line);
}

.sm-row-label {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--kb-text-2);
  font-size: 13px;
}

/* 说明收进悬浮：默认不占版面，与设计样例的减字口径一致 */
.sm-help {
  display: grid;
  width: 14px;
  height: 14px;
  border: 1px solid var(--kb-line-strong);
  border-radius: 50%;
  color: var(--kb-text-3);
  font-size: 9px;
  font-style: normal;
  cursor: help;
  place-items: center;
}

.sm-help:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

.sm-row-value {
  color: var(--kb-text-1);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.sm-params {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 10px 0;
  border-bottom: 1px solid var(--kb-line);
}

.sm-param {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.sm-param-label {
  color: var(--kb-text-3);
  font-size: 12px;
}

.sm-param-input {
  width: 100%;
}

/* 子开关：缩进 + 更小字号，与主规则区分层级。
   三列而不是两列——两列时每格太窄，标签与开关会挤在一起（标签居左、开关居右，中间没余量） */
.sm-sub {
  display: grid;
  gap: 2px 26px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 10px 0 10px 14px;
  border-bottom: 1px solid var(--kb-line);
}

.sm-sub-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 3px 0;
}

.sm-sub-label {
  color: var(--kb-text-3);
  font-size: 12px;
}
</style>
