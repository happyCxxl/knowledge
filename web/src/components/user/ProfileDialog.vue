<template>
  <el-dialog
    v-model="visible"
    title="个人信息"
    width="420px"
    append-to-body
    destroy-on-close
    @open="loadProfile"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="用户名">
        <el-input :model-value="username" disabled />
      </el-form-item>
      <el-form-item prop="displayName" label="真实姓名">
        <el-input v-model="form.displayName" placeholder="真实姓名" />
      </el-form-item>
      <el-form-item prop="email" label="邮箱">
        <el-input v-model="form.email" placeholder="选填" autocomplete="email" />
      </el-form-item>
      <el-form-item prop="phone" label="手机号">
        <el-input v-model="form.phone" placeholder="选填" autocomplete="tel" />
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
import { computed, reactive, ref } from 'vue';

import { getProfile, updateProfile } from '@/api/user';
import { useAuthStore } from '@/stores/auth';

// 个人信息弹窗：本人修改真实姓名、邮箱、手机号（接口层已统一处理失败提示）
const visible = defineModel<boolean>({ required: true });

/** 保存成功回调，携带最新的真实姓名（供外层更新顶栏展示） */
const emit = defineEmits<{ saved: [displayName: string] }>();

const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const submitting = ref(false);

// 用户名是登录凭据与留痕口径，只读展示
const username = computed(() => authStore.username ?? '');

const form = reactive({
  displayName: '',
  email: '',
  phone: '',
});

const rules: FormRules = {
  displayName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { max: 64, message: '真实姓名不能超过 64 位', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+()\- ]{6,32}$/, message: '手机号格式不正确', trigger: 'blur' }],
};

// 打开时取一次服务端资料：换设备登录或管理员改过之后，本地缓存可能不是最新的
async function loadProfile(): Promise<void> {
  formRef.value?.clearValidate();
  try {
    const profile = await getProfile();
    form.displayName = profile.displayName ?? '';
    form.email = profile.email ?? '';
    form.phone = profile.phone ?? '';
  } catch {
    // 失败提示已由接口层统一拦截处理
  }
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
    await updateProfile({
      displayName: form.displayName,
      email: form.email,
      phone: form.phone,
    });
    ElMessage.success('保存成功');
    emit('saved', form.displayName);
    visible.value = false;
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    submitting.value = false;
  }
}
</script>
