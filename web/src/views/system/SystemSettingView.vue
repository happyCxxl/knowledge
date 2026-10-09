<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">系统设置</h1>
      </div>
    </div>

    <div class="setting-body">
      <section class="setting-card">
        <h2 class="setting-card-title">文件服务写入后端</h2>

        <!-- 启用入口：下拉只列已启用的存储源，选中项就是当前项时按钮置灰 -->
        <div class="setting-pick">
          <span class="setting-field-label">存储源</span>
          <el-select
            v-model="pickedId"
            class="setting-select"
            placeholder="暂无启用的存储源"
            :disabled="enabledSources.length === 0"
          >
            <el-option
              v-for="source in enabledSources"
              :key="source.id"
              :label="sourceLabel(source)"
              :value="source.id"
            />
          </el-select>
          <button
            class="setting-btn-primary"
            type="button"
            :disabled="enableDisabled"
            @click="handleEnable"
          >
            {{ enableLabel }}
          </button>
        </div>

        <!-- 当前启用块：内嵌浅底面板，随内容撑开，字段多时面板内滚动 -->
        <div class="setting-panel">
          <div class="setting-panel-head">
            <span class="setting-panel-k">当前启用</span>
            <template v-if="currentSource !== null">
              <span class="setting-panel-name">{{ currentSource.name }}</span>
              <span class="setting-spacer"></span>
              <span
                class="setting-chip"
                :class="{
                  'is-warn': sourceStateTone(currentSource) === 'warn',
                  'is-mute': sourceStateTone(currentSource) === 'mute',
                }"
                :title="sourceStateTitle(currentSource)"
              >
                {{ sourceStateText(currentSource) }}
              </span>
            </template>
          </div>
          <div class="setting-panel-body">
            <p v-if="currentSource === null" class="setting-empty">
              {{ loading ? '加载中…' : '暂无启用的存储源' }}
            </p>
            <template v-else>
              <div class="setting-kv">
                <span class="setting-kv-k">类型</span>
                <span class="setting-kv-v">
                  {{ currentSource.storageTypeName }}
                  <span class="setting-mono setting-type-code">{{
                    currentSource.storageType
                  }}</span>
                </span>
              </div>
              <div v-if="currentSource.credentialConfigured" class="setting-kv">
                <span class="setting-kv-k">密钥</span>
                <span class="setting-kv-v is-dim">已配置</span>
              </div>
              <div v-for="entry in paramEntries" :key="entry.label" class="setting-kv">
                <span class="setting-kv-k">{{ entry.label }}</span>
                <span class="setting-kv-v setting-mono">{{ entry.value }}</span>
              </div>
            </template>
          </div>
        </div>
      </section>
    </div>

    <!-- 启用确认：先取影响面，确认后才真正切换 -->
    <div v-if="pending !== null" class="setting-mask" @click.self="pending = null">
      <div class="setting-dialog" role="dialog" aria-modal="true">
        <h3 class="setting-dialog-title">启用 {{ pending.targetName }}</h3>
        <p class="setting-dialog-line">{{ pending.message }}</p>
        <div class="setting-impact">
          <div class="setting-impact-cell">
            <span class="setting-impact-k">排队中 · 将跑不了</span>
            <span class="setting-impact-v">{{ pending.queuedCount }}</span>
          </div>
          <div class="setting-impact-cell">
            <span class="setting-impact-k">执行中 · 将跑不了</span>
            <span class="setting-impact-v">{{ pending.runningCount }}</span>
          </div>
        </div>
        <div class="setting-dialog-foot">
          <button
            class="setting-btn-ghost"
            type="button"
            :disabled="switching"
            @click="pending = null"
          >
            取消
          </button>
          <button
            class="setting-btn-primary"
            type="button"
            :disabled="switching"
            @click="confirmEnable"
          >
            确认启用
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref } from 'vue';

import {
  getStorageSourceCurrentPreview,
  getStorageSourceList,
  updateStorageSourceCurrent,
} from '@/api/system';
import type { StorageSourceSwitchVO, StorageSourceVO } from '@/types/system';
import { formatDate, formatTime } from '@/utils/date';

// 系统设置：一张卡管文件服务的写入后端；启用走两步 —— 先取影响面，确认后再切换

/** 参数键的展示名：只覆盖已知键，未知键显示原始键 */
const PARAM_LABELS = new Map<string, string>([
  ['endpoint', '服务端点'],
  ['fileBucket', '文件桶'],
  ['artifactBucket', '产物桶'],
  ['rootDir', '根目录'],
  ['fileDir', '文件目录'],
  ['artifactDir', '产物目录'],
]);

const sources = ref<StorageSourceVO[]>([]);
const loading = ref(false);
const switching = ref(false);
const pickedId = ref('');
const pending = ref<StorageSourceSwitchVO | null>(null);

/** 当前启用的写入存储源 */
const currentSource = computed(() => sources.value.find((item) => item.current) ?? null);

/** 下拉候选：状态为已启用的存储源 */
const enabledSources = computed(() => sources.value.filter((item) => item.status === 'ENABLED'));

/** 下拉选中项就是当前启用项 */
const pickedIsCurrent = computed(
  () => pickedId.value !== '' && pickedId.value === currentSource.value?.id,
);

/** 当前启用存储源的参数摘要：键换成展示名，密钥类参数不在其中 */
const paramEntries = computed(() =>
  Object.entries(currentSource.value?.params ?? {}).map(([key, value]) => ({
    label: paramLabel(key),
    value,
  })),
);

/** 启用按钮文案：选中项就是当前项时读作已启用 */
const enableLabel = computed(() => (pickedIsCurrent.value ? '已启用' : '启用'));

/** 启用按钮：选中项已启用、没有候选或切换中时置灰 */
const enableDisabled = computed(
  () => switching.value || pickedId.value === '' || pickedIsCurrent.value,
);

/**
 * 行的状态文案：四态结论，带最近一次探测时间。
 *
 * @param source 清单里的存储源
 * @returns 已接入、连接失败、未测试或未接入，附探测时间
 */
function sourceStateText(source: StorageSourceVO): string {
  const label = sourceStateLabel(source);
  if (source.probeAt === null) {
    return label;
  }
  return `${label} · 最近探测 ${probeStamp(source.probeAt)}`;
}

/**
 * 探测时间戳：yyyy-MM-dd 只留月日，拼成 MM-DD HH:mm。
 *
 * @param value 后端 ISO 时间
 * @returns 月日与时分
 */
function probeStamp(value: string): string {
  return `${formatDate(value).slice(5)} ${formatTime(value)}`;
}

/**
 * 行的状态文案：未注册进运行时读作未接入，注册后按最近一次连接探测的结论读。
 *
 * @param source 清单里的存储源
 * @returns 已接入、连接失败、未测试或未接入
 */
function sourceStateLabel(source: StorageSourceVO): string {
  if (!source.registered) {
    return '未接入';
  }
  if (source.probeOk === true) {
    return '已接入';
  }
  return source.probeOk === false ? '连接失败' : '未测试';
}

/**
 * 行的状态色调：已接入绿点，连接失败黄点，未测试与未接入灰点。
 *
 * @param source 清单里的存储源
 * @returns ok、warn 或 mute
 */
function sourceStateTone(source: StorageSourceVO): string {
  if (!source.registered) {
    return 'mute';
  }
  if (source.probeOk === true) {
    return 'ok';
  }
  return source.probeOk === false ? 'warn' : 'mute';
}

/**
 * 行的状态悬停提示：连接失败时给出最近一次探测时间。
 *
 * @param source 清单里的存储源
 * @returns 悬停提示
 */
function sourceStateTitle(source: StorageSourceVO): string {
  if (source.probeOk !== false || source.probeAt === null) {
    return '';
  }
  return `最近探测 ${formatDate(source.probeAt)} ${formatTime(source.probeAt)}`;
}

/**
 * 参数的展示名：已知键取展示名，未知键显示原始键。
 *
 * @param key 参数键
 * @returns 展示名
 */
function paramLabel(key: string): string {
  return PARAM_LABELS.get(key) ?? key;
}

/**
 * 下拉选项文案：名称 + 类型码。
 *
 * @param source 清单里的存储源
 * @returns 选项文案
 */
function sourceLabel(source: StorageSourceVO): string {
  return `${source.name} · ${source.storageType}`;
}

/** 取一次存储源清单；选中项不在候选里时回落到当前启用的存储源 */
async function loadSources(): Promise<void> {
  loading.value = true;
  try {
    const list = await getStorageSourceList();
    sources.value = list;
    const candidates = list.filter((item) => item.status === 'ENABLED');
    const kept = candidates.find((item) => item.id === pickedId.value);
    const current = candidates.find((item) => item.current);
    if (kept === undefined) {
      pickedId.value = current === undefined ? '' : current.id;
    }
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    loading.value = false;
  }
}

/** 启用第一步：取影响面并弹确认窗 */
async function handleEnable(): Promise<void> {
  if (enableDisabled.value) {
    return;
  }
  switching.value = true;
  try {
    pending.value = await getStorageSourceCurrentPreview(pickedId.value);
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    switching.value = false;
  }
}

/** 启用第二步：确认后真正切换，成功后重取一次清单让当前启用跟着走 */
async function confirmEnable(): Promise<void> {
  const target = pending.value;
  if (target === null || switching.value) {
    return;
  }
  switching.value = true;
  try {
    await updateStorageSourceCurrent(target.targetSourceId);
    pending.value = null;
    await loadSources();
    ElMessage.success(`已启用${target.targetName}`);
  } catch {
    // 失败提示已由接口层统一拦截
  } finally {
    switching.value = false;
  }
}

onMounted(() => {
  void loadSources();
});
</script>

<style scoped lang="css">
/*
 * 页面骨架取自全局 styles/page-shell.css（根元素用 .page、标题用 .page-title）；
 * 卡片与卡内控件由本页私有类承载，卡片表面取值与个人中心的 .profile-card 同一套。
 */

/* 卡片区：一屏放不下时只滚这里 */
.setting-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* 卡片：高度随内容，长到上限后卡内面板自己滚 */
.setting-card {
  display: flex;
  flex-direction: column;
  width: 390px;
  max-height: 300px;
  padding: 14px 16px;
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: var(--kb-radius);
  background: linear-gradient(180deg, rgb(255 255 255 / 6%), rgb(255 255 255 / 2%));
  box-shadow:
    0 1px 0 rgb(255 255 255 / 6%) inset,
    0 12px 30px rgb(0 0 0 / 32%);
}

.setting-card-title {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 650;
}

/* 标题前的小色块：卡片的视觉锚点 */
.setting-card-title::before {
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--kb-primary);
  box-shadow: 0 0 0 3px rgb(52 211 153 / 14%);
  content: '';
}

/* 启用入口：下拉占满标签与按钮之间的位置 */
.setting-pick {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
}

.setting-field-label {
  color: var(--kb-text-2);
  font-size: 13px;
  white-space: nowrap;
}

.setting-select {
  flex: 1;
}

/* 文字控件统一字体栈：button 默认用系统字体，不写会与页面其它文字不一致 */
.setting-btn-primary,
.setting-btn-ghost {
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  cursor: pointer;
}

.setting-btn-primary {
  padding: 7px 16px;
  border: none;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 5px 16px rgb(52 211 153 / 22%);
  color: var(--kb-btn-text);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* 置灰：不可点的按钮去掉底色与投影，只留文字 */
.setting-btn-primary:disabled {
  background: rgb(255 255 255 / 6%);
  box-shadow: none;
  color: var(--kb-text-4);
  cursor: default;
}

.setting-btn-ghost {
  padding: 7px 15px;
  border: 1px solid var(--kb-line-strong);
  border-radius: 9px;
  background: rgb(255 255 255 / 4%);
  color: var(--kb-text-1);
  font-size: 12px;
}

/* 当前启用块：内嵌浅底面板，随内容撑开 */
.setting-panel {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--kb-line);
  border-radius: 10px;
  background: rgb(255 255 255 / 3%);
}

/* 面板头：小字标签 + 名称 + 弹性空隙 + 状态胶囊 */
.setting-panel-head {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
}

/* 面板头小字标签：不参与压缩，单行 */
.setting-panel-k {
  flex: none;
  color: var(--kb-text-4);
  font-size: 11px;
  white-space: nowrap;
}

/* 名称：窄卡里长名称单行省略，状态胶囊不被挤出面板 */
.setting-panel-name {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setting-spacer {
  flex: 1;
}

/* 状态胶囊：描边小标签，点是四态结论的落点；文案单行不折 */
.setting-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 2px 9px;
  border: 1px solid var(--kb-line-2);
  border-radius: 999px;
  color: var(--kb-text-2);
  font-size: 11px;
  white-space: nowrap;
}

.setting-chip::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--kb-primary);
  content: '';
}

/* 连接失败：点用警示色，文字沿用常规亮度 */
.setting-chip.is-warn::before {
  background: var(--kb-warn);
}

/* 未测试与未接入：点与文字一起压暗 */
.setting-chip.is-mute {
  color: var(--kb-text-3);
}

.setting-chip.is-mute::before {
  background: var(--kb-text-4);
}

/* 参数区：一行一项铺开，内容超出时面板内滚动 */
.setting-panel-body {
  display: grid;
  flex: 1 1 auto;
  gap: 8px;
  align-content: start;
  min-height: 0;
  overflow: auto;
  grid-template-columns: minmax(0, 1fr);
}

.setting-empty {
  margin: 0;
  color: var(--kb-text-3);
  font-size: 12px;
}

.setting-kv {
  display: flex;
  gap: 10px;
  align-items: baseline;
  font-size: 12px;
}

.setting-kv-k {
  flex: none;
  width: 60px;
  color: var(--kb-text-4);
}

/* 单行省略：长名称与长参数值都不换行 */
.setting-kv-v {
  min-width: 0;
  overflow: hidden;
  color: var(--kb-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.setting-kv-v.is-dim {
  color: var(--kb-text-3);
}

.setting-mono {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
}

/* 类型码：跟在类型显示名之后，压暗一档 */
.setting-type-code {
  color: var(--kb-text-4);
}

/* 遮罩：铺满视口，点空白处关闭 */
.setting-mask {
  position: fixed;
  z-index: 2000;
  display: grid;
  background: rgb(7 11 20 / 70%);
  inset: 0;
  place-items: center;
}

.setting-dialog {
  width: 420px;
  padding: 18px 20px 16px;
  border: 1px solid var(--kb-line-2);
  border-radius: 14px;
  background: var(--kb-bg-2);
  box-shadow: 0 24px 60px rgb(0 0 0 / 55%);
}

.setting-dialog-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 650;
}

.setting-dialog-line {
  margin: 0;
  color: var(--kb-text-2);
  font-size: 12px;
  line-height: 1.8;
}

/* 影响面：两个计数并排，用警示色与正文分开 */
.setting-impact {
  display: flex;
  gap: 10px;
  margin: 12px 0 0;
  padding: 10px 12px;
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 10px;
  background: rgb(251 191 36 / 6%);
}

.setting-impact-cell {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.setting-impact-k {
  color: var(--kb-text-4);
  font-size: 11px;
}

.setting-impact-v {
  color: var(--kb-warn);
  font-size: 17px;
  font-weight: 650;
}

.setting-dialog-foot {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
