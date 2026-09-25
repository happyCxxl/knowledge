<template>
  <div class="user-page">
    <div class="user-page-head">
      <div class="user-page-title-wrap">
        <h1 class="user-page-title">用户管理</h1>
        <p class="user-page-desc">维护平台账号与角色，支持按用户名、角色、状态筛选</p>
      </div>
      <div class="user-actions">
        <el-button class="user-btn-primary" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <div class="user-panel">
      <!-- 筛选栏：搜索与筛选集中一处，与表体、分页同属一个面板 -->
      <div class="user-filter-bar">
        <el-input
          v-model="keyword"
          class="user-search"
          placeholder="搜索用户名"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="roleFilter" class="user-select" placeholder="角色">
          <el-option label="全部角色" value="" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
        <el-select v-model="statusFilter" class="user-select" placeholder="状态">
          <el-option label="全部状态" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button class="user-btn-primary" @click="handleSearch">查询</el-button>
        <el-button class="user-btn-ghost" @click="handleReset">重置</el-button>
        <button class="user-refresh" type="button" title="刷新" @click="loadUsers">
          <svg
            class="user-refresh-icon"
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
        <span class="user-filter-count">共 {{ total }} 个账号</span>
      </div>

      <!-- 固定高度滚动区：高度由祖先链的确定高度决定，不随记录数变化；
           el-table 拿到确定高度后表头冻结、表体内部滚动 -->
      <div class="user-table-body">
        <el-table
          v-loading="loading"
          class="user-table"
          height="100%"
          :data="records"
          :border="false"
        >
          <el-table-column prop="username" label="用户名" min-width="160" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" effect="plain" size="small">
                {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建人" width="120">
            <template #default="{ row }">{{ row.createBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button
                class="user-row-btn"
                link
                type="primary"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? '不能编辑当前登录账号' : ''"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                class="user-row-btn"
                link
                type="danger"
                :disabled="isSelf(row)"
                :title="isSelf(row) ? '不能删除当前登录账号' : ''"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="user-empty">{{ loading ? '加载中…' : '暂无用户' }}</div>
          </template>
        </el-table>
      </div>

      <div class="user-panel-foot">
        <span>每页 {{ query.size }} 条</span>
        <el-pagination
          class="user-pager"
          layout="prev, pager, next"
          :total="total"
          :page-size="query.size"
          :current-page="query.current"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑：同一弹窗，编辑时用户名只读、密码留空表示不重置 -->
    <el-dialog
      v-model="dialogVisible"
      class="user-dialog"
      :title="isEdit ? '编辑用户' : '新增用户'"
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
        <el-form-item prop="username" label="用户名" class="user-dialog-item">
          <el-input
            v-model="dialogForm.username"
            class="login-input"
            placeholder="3-32 位用户名"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item prop="password" label="密码" class="user-dialog-item">
          <el-input
            v-model="dialogForm.password"
            class="login-input"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示不重置密码' : '6-64 位密码'"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword" label="确认密码" class="user-dialog-item">
          <el-input
            v-model="dialogForm.confirmPassword"
            class="login-input"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示不重置密码' : '请再次输入密码'"
          />
        </el-form-item>
        <el-form-item prop="role" label="角色" class="user-dialog-item">
          <el-select v-model="dialogForm.role" class="user-dialog-select" :disabled="isEditSelf">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item prop="status" label="状态" class="user-dialog-item">
          <el-radio-group v-model="dialogForm.status" :disabled="isEditSelf">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="user-btn-ghost" @click="dialogVisible = false">取消</el-button>
        <el-button class="user-btn-primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { addUserByAdmin, deleteUser, getUserPage, updateUser } from '@/api/user';
import { useAuthStore } from '@/stores/auth';
import type { UserVO } from '@/types/user';

// 用户管理页：分页查询 + 新增/编辑/删除（接口层已统一处理失败提示，页面只处理成功分支）
const authStore = useAuthStore();

const query = reactive({ current: 1, size: 10 });

// 筛选条件：空串表示「全部」，请求时统一转成 undefined（不传该查询参数）
const roleFilter = ref<string>('');
const statusFilter = ref<number | string>('');

const keyword = ref('');
const records = ref<UserVO[]>([]);
const total = ref(0);
const loading = ref(false);

const dialogVisible = ref(false);
const submitting = ref(false);
const editingId = ref<string | null>(null);
const dialogFormRef = ref<FormInstance>();

const dialogForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: 'USER',
  status: 1,
});

const isEdit = computed(() => editingId.value !== null);
// 后端同样拒绝改自己的角色/状态，这里提前禁用输入
const isEditSelf = computed(() => isEdit.value && editingId.value === authStore.userId);

const dialogRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度须为 3-32 位', trigger: 'blur' },
  ],
  password: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!isEdit.value && !value) {
          callback(new Error('请输入密码'));
          return;
        }
        if (value && (value.length < 6 || value.length > 64)) {
          callback(new Error('密码长度须为 6-64 位'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== dialogForm.password) {
          callback(new Error('两次输入的密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

/** 时间展示：后端下发 ISO 字符串，这里统一按 yyyy-MM-dd HH:mm 呈现 */
function formatTime(value: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

/** 是否为当前登录账号：后端禁止操作自身，前端提前禁用按钮 */
function isSelf(row: UserVO): boolean {
  return authStore.userId !== null && row.id === authStore.userId;
}

async function loadUsers(): Promise<void> {
  loading.value = true;
  try {
    const page = await getUserPage({
      current: query.current,
      size: query.size,
      username: keyword.value.trim() || undefined,
      role: roleFilter.value === '' ? undefined : roleFilter.value,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
    });
    records.value = page.records;
    total.value = page.total;
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    loading.value = false;
  }
}

/** 查询条件变化后回到第一页，避免停留在越界页码 */
function handleSearch(): void {
  query.current = 1;
  void loadUsers();
}

/** 重置全部筛选条件并重新查询 */
function handleReset(): void {
  keyword.value = '';
  roleFilter.value = '';
  statusFilter.value = '';
  handleSearch();
}

function handlePageChange(current: number): void {
  query.current = current;
  void loadUsers();
}

function resetDialogForm(): void {
  dialogForm.username = '';
  dialogForm.password = '';
  dialogForm.confirmPassword = '';
  dialogForm.role = 'USER';
  dialogForm.status = 1;
}

function openCreate(): void {
  editingId.value = null;
  resetDialogForm();
  dialogVisible.value = true;
}

function openEdit(row: UserVO): void {
  editingId.value = row.id;
  dialogForm.username = row.username;
  dialogForm.password = '';
  dialogForm.confirmPassword = '';
  dialogForm.role = row.role === 'ADMIN' ? 'ADMIN' : 'USER';
  dialogForm.status = row.status;
  dialogVisible.value = true;
}

function handleDialogClosed(): void {
  editingId.value = null;
  resetDialogForm();
}

async function handleSubmit(): Promise<void> {
  const valid = await dialogFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    if (editingId.value === null) {
      await addUserByAdmin({
        username: dialogForm.username,
        password: dialogForm.password,
        role: dialogForm.role,
        status: dialogForm.status,
      });
      ElMessage.success('新增成功');
    } else {
      await updateUser(editingId.value, {
        // 留空表示不重置密码，不把空串发给后端
        password: dialogForm.password || undefined,
        role: dialogForm.role,
        status: dialogForm.status,
      });
      ElMessage.success('更新成功');
    }
    dialogVisible.value = false;
    await loadUsers();
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: UserVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确认删除用户「${row.username}」？删除后该账号无法登录。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  ).catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await deleteUser(row.id);
    ElMessage.success('删除成功');
    // 删掉当前页最后一条时回退一页，避免停留在空列表（必须在重载前调页）
    if (records.value.length === 1 && query.current > 1) {
      query.current -= 1;
    }
    await loadUsers();
  } catch {
    // 失败提示已由接口层统一拦截处理
  }
}

onMounted(() => {
  void loadUsers();
});
</script>

<style scoped lang="css">
.user-page {
  display: flex;
  flex-direction: column;
  gap: 18px;

  /* 固定高度：等于「视口 - 顶栏(62px) - 内容区上下内边距(26+40)」，不随记录数变化 */
  height: calc(100vh - 128px);
  min-height: 420px;
}

.user-page-head {
  display: flex;
  flex: none;
  align-items: flex-end;
  justify-content: space-between;
}

.user-page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}

.user-page-desc {
  margin: 6px 0 0;
  color: var(--kb-text-3);
  font-size: 13px;
}

.user-actions {
  display: flex;
  gap: 10px;
}

/* 筛选栏：搜索 + 角色 + 状态 + 查询/重置 + 刷新 + 计数，同属面板顶部一行 */
.user-filter-bar {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--kb-line);
}

.user-search {
  width: 200px;
}

.user-select {
  width: 130px;
}

.user-filter-count {
  margin-left: auto;
  color: var(--kb-text-3);
  font-size: 12px;
}

.user-btn-ghost {
  border-color: var(--kb-line-strong);
  background: rgb(255 255 255 / 4%);
  color: var(--kb-text-1);
}

.user-btn-primary {
  border: none;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 6px 22px rgb(52 211 153 / 25%);
  color: var(--kb-btn-text);
}

.user-btn-primary:hover,
.user-btn-primary:focus {
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 8px 28px var(--kb-glow);
  color: var(--kb-btn-text);
  filter: brightness(1.08);
}

.user-panel {
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

.user-refresh {
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

.user-refresh:hover {
  border-color: var(--kb-primary);
  color: var(--kb-primary);
}

/* 表体滚动区：flex:1 + min-height:0 拿到确定高度，el-table 据此固定表头并内部滚动 */
.user-table-body {
  flex: 1;
  min-height: 0;
}

.user-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--kb-bg-1);
  --el-table-border-color: var(--kb-line);
  --el-table-text-color: var(--kb-text-1);
  --el-table-header-text-color: var(--kb-text-2);
  --el-table-row-hover-bg-color: rgb(52 211 153 / 6%);

  background: transparent;
}

.user-row-btn {
  padding: 0 6px;
}

.user-empty {
  padding: 44px 0;
  color: var(--kb-text-3);
  font-size: 13px;
}

.user-panel-foot {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  padding: 13px 18px;
  border-top: 1px solid var(--kb-line);
  color: var(--kb-text-3);
  font-size: 13px;
}

.user-dialog-select {
  width: 100%;
}
</style>
