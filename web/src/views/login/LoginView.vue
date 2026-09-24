<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-aurora"></div>
    <div class="login-grid"></div>
    <div class="login-stars"></div>
    <div class="login-stars-two"></div>
    <main class="login-main">
      <section class="login-card">
        <div class="login-brand">
          <div class="login-logo">
            <svg
              class="login-logo-mark"
              width="26"
              height="26"
              viewBox="0 0 26 26"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M13 2.5 22 7.5v11L13 23.5 4 18.5v-11L13 2.5Z"
                stroke="#041510"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
              <path
                d="M9.2 14.6v-3.2l3.8-6.2 3.8 6.2v3.2l-3.8 5.8-3.8-5.8Z"
                stroke="#041510"
                stroke-width="1.6"
                stroke-linejoin="round"
                opacity="0.85"
              />
            </svg>
          </div>
          <div class="login-brand-text">
            <div class="login-brand-name">knowledge</div>
            <div class="login-brand-sub">企业知识库 · 检索评测平台</div>
          </div>
        </div>
        <h1 class="login-title">欢迎回来</h1>
        <p class="login-sub">登录以继续你的知识工作</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          :hide-required-asterisk="true"
          class="login-form"
        >
          <el-form-item prop="account" label="账号" class="login-item">
            <el-input
              v-model="form.account"
              class="login-input"
              placeholder="请输入账号"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password" label="密码" class="login-item">
            <el-input
              v-model="form.password"
              class="login-input"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <div class="login-row">
            <el-checkbox v-model="form.remember" class="login-remember">记住我</el-checkbox>
            <a class="login-forget" href="#">忘记密码？</a>
          </div>
          <el-button class="login-button" @click="handleSubmit">登 录</el-button>
        </el-form>
        <div class="login-foot">knowledge · 企业内部知识管理平台</div>
      </section>
    </main>
    <div class="login-status">
      <span class="login-dot"></span>
      系统运行正常
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { reactive, ref } from 'vue';

// 登录页：品牌展示与账号密码登录表单
const formRef = ref<FormInstance>();

const form = reactive({
  account: '',
  password: '',
  remember: true,
});

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

// 校验表单；登录接口接入前先提示待接入
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  ElMessage.info('登录接口待接入');
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: var(--kb-bg-0);
}

.login-bg {
  position: fixed;
  inset: 0;
  background:
    radial-gradient(1100px 700px at 78% -10%, var(--kb-aurora), transparent 62%),
    radial-gradient(900px 620px at -8% 108%, var(--kb-aurora), transparent 60%),
    linear-gradient(180deg, var(--kb-bg-0), var(--kb-bg-1) 55%, var(--kb-bg-2));
}

.login-aurora {
  position: fixed;
  inset: -25%;
  background: conic-gradient(
    from 210deg at 60% 30%,
    transparent 0deg,
    var(--kb-aurora) 70deg,
    transparent 150deg,
    var(--kb-aurora) 250deg,
    transparent 360deg
  );
  filter: blur(90px);
  opacity: 0.5;
  animation: login-breathe 16s ease-in-out infinite alternate;
}

.login-grid {
  position: fixed;
  inset: 0;
  background-image:
    linear-gradient(rgb(255 255 255 / 2.8%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 2.8%) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 90% 70% at 50% 40%, #000 25%, transparent 78%);
}

.login-stars {
  position: fixed;
  top: 0;
  left: 0;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: rgb(230 234 242 / 45%);
  box-shadow:
    86px 210px 0 0 rgb(230 234 242 / 38%),
    340px 60px 0 0 rgb(230 234 242 / 50%),
    512px 340px 0 0 rgb(230 234 242 / 30%),
    640px 110px 0 0 rgb(230 234 242 / 44%),
    830px 420px 0 0 rgb(230 234 242 / 28%),
    1010px 160px 0 0 rgb(230 234 242 / 52%),
    1180px 380px 0 0 rgb(230 234 242 / 34%),
    1360px 80px 0 0 rgb(230 234 242 / 46%),
    1520px 300px 0 0 rgb(230 234 242 / 30%),
    1700px 130px 0 0 rgb(230 234 242 / 50%),
    1880px 420px 0 0 rgb(230 234 242 / 36%),
    120px 620px 0 0 rgb(230 234 242 / 32%),
    380px 780px 0 0 rgb(230 234 242 / 42%),
    660px 900px 0 0 rgb(230 234 242 / 30%),
    920px 700px 0 0 rgb(230 234 242 / 40%),
    1240px 880px 0 0 rgb(230 234 242 / 34%),
    1480px 640px 0 0 rgb(230 234 242 / 44%),
    1660px 820px 0 0 rgb(230 234 242 / 30%),
    1750px 560px 0 0 rgb(230 234 242 / 38%),
    2040px 700px 0 0 rgb(230 234 242 / 34%),
    -40px 420px 0 0 rgb(230 234 242 / 28%);
  animation: login-twinkle 7s ease-in-out infinite alternate;
}

.login-stars-two {
  position: fixed;
  top: 0;
  left: 0;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--kb-glow);
  box-shadow:
    430px 240px 0 0 var(--kb-glow),
    1090px 560px 0 0 var(--kb-glow),
    1570px 190px 0 0 var(--kb-glow);
  animation: login-twinkle 5s ease-in-out infinite alternate-reverse;
}

.login-main {
  position: relative;
  z-index: 1;
  display: grid;
  height: 100%;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(428px, 100%);
  padding: 44px 40px 36px;
  border: 1px solid var(--kb-line);
  border-radius: 20px;
  background: linear-gradient(180deg, rgb(255 255 255 / 6%), rgb(255 255 255 / 2.8%));
  box-shadow:
    0 30px 90px rgb(0 0 0 / 50%),
    inset 0 1px 0 rgb(255 255 255 / 7%);
  backdrop-filter: blur(28px);
  animation: login-rise 0.55s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}

.login-brand {
  display: flex;
  gap: 13px;
  align-items: center;
  margin-bottom: 34px;
}

.login-logo {
  display: grid;
  flex: none;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 13px;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 0 26px var(--kb-glow);
}

.login-brand-name {
  font-size: 23px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.login-brand-sub {
  color: var(--kb-text-3);
  font-size: 12px;
  letter-spacing: 0.14em;
}

.login-title {
  margin: 0 0 8px;
  font-size: 27px;
  font-weight: 650;
  background: linear-gradient(90deg, var(--kb-text-1), var(--kb-primary));
  background-clip: text;
  color: transparent;
}

.login-sub {
  margin: 0 0 30px;
  color: var(--kb-text-2);
  font-size: 14px;
}

.login-item {
  margin-bottom: 0;
}

.login-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.login-forget {
  color: var(--kb-text-2);
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
}

.login-forget:hover {
  color: var(--kb-primary);
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 26px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 6px 22px rgb(52 211 153 / 25%);
  color: var(--kb-btn-text);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.24em;
  transition:
    filter 0.15s,
    transform 0.15s,
    box-shadow 0.2s;
}

.login-button:hover,
.login-button:focus {
  background: linear-gradient(135deg, var(--kb-primary), var(--kb-primary-2));
  box-shadow: 0 10px 30px var(--kb-glow);
  color: var(--kb-btn-text);
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.login-button:active {
  filter: brightness(0.97);
  transform: none;
}

.login-foot {
  margin-top: 30px;
  color: var(--kb-text-3);
  font-size: 12px;
  letter-spacing: 0.06em;
  text-align: center;
}

.login-status {
  position: fixed;
  bottom: 22px;
  left: 50%;
  z-index: 1;
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--kb-text-3);
  font-size: 12px;
  letter-spacing: 0.08em;
  transform: translateX(-50%);
}

.login-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--kb-ok);
  box-shadow: 0 0 10px rgb(163 230 53 / 80%);
  animation: login-pulse 2.4s ease-in-out infinite;
}

@keyframes login-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes login-breathe {
  from {
    transform: translate3d(-2%, -1%, 0) rotate(0deg) scale(1);
  }

  to {
    transform: translate3d(2%, 1%, 0) rotate(7deg) scale(1.07);
  }
}

@keyframes login-twinkle {
  from {
    opacity: 0.35;
  }

  to {
    opacity: 0.95;
  }
}

@keyframes login-pulse {
  0% {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }

  100% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-page * {
    animation: none !important;
    transition: none !important;
  }
}
</style>
