<template>
  <el-dialog
    v-model="visible"
    title="修改密码"
    width="420px"
    append-to-body
    destroy-on-close
    @open="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item prop="oldPassword" label="当前密码">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="当前密码"
        />
      </el-form-item>
      <el-form-item prop="newPassword" label="新密码">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="8-64 位密码"
        />
      </el-form-item>
      <el-form-item prop="confirmPassword" label="确认新密码">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { reactive, ref } from 'vue';

import { updatePassword } from '@/api/user';
import { useAuthStore } from '@/stores/auth';

// 修改密码弹窗：校验当前密码后写入新密码；服务端会递增令牌版本，因此本地令牌必须换成响应里补签的那张
const visible = defineModel<boolean>({ required: true });

const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度须为 8-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

/** 清空输入与校验状态，避免上次的输入和报错残留 */
function resetForm(): void {
  form.oldPassword = '';
  form.newPassword = '';
  form.confirmPassword = '';
  formRef.value?.clearValidate();
}

async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return;
  }
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    const token = await updatePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    authStore.replaceToken(token);
    ElMessage.success('密码已修改，其他设备需用新密码重新登录');
    visible.value = false;
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    submitting.value = false;
  }
}
</script>
