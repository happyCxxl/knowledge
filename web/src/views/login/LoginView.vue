<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-aurora"></div>
    <div class="login-grid"></div>
    <div class="login-stars"></div>
    <div class="login-stars-two"></div>
    <main class="login-main">
      <!-- 3D 舞台：两张卡片互为背面，切换时绕水平轴竖向翻转 -->
      <div ref="flipRef" class="login-flip" :class="{ flipping }">
        <div
          ref="loginCard"
          class="login-card"
          :class="{ 'login-card-back': cardSide !== 'login' }"
          :aria-hidden="cardSide !== 'login'"
        >
          <div class="login-face">
            <span class="login-sheen"></span>
            <div class="login-head">
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
              <div class="login-head-text">
                <div class="login-brand">knowledge</div>
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
                  :tabindex="cardSide === 'login' ? 0 : -1"
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
                  :tabindex="cardSide === 'login' ? 0 : -1"
                />
              </el-form-item>
              <div class="login-row">
                <el-checkbox v-model="form.remember" class="login-remember">记住我</el-checkbox>
                <a class="login-forget" href="#">忘记密码？</a>
              </div>
              <el-button
                class="login-button"
                :loading="loading"
                :tabindex="cardSide === 'login' ? 0 : -1"
                @click="handleSubmit"
              >
                登 录
              </el-button>
            </el-form>
            <div class="login-switch">
              <span class="login-switch-text">还没有账号？</span>
              <button
                class="login-switch-link"
                type="button"
                :tabindex="cardSide === 'login' ? 0 : -1"
                @click="switchMode('register')"
              >
                立即注册
              </button>
            </div>
            <div class="login-foot">knowledge · 企业内部知识管理平台</div>
          </div>
        </div>
        <div
          ref="registerCard"
          class="login-card"
          :class="{ 'login-card-back': cardSide !== 'register' }"
          :aria-hidden="cardSide !== 'register'"
        >
          <div class="login-face">
            <span class="login-sheen"></span>
            <div class="login-head">
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
              <div class="login-head-text">
                <div class="login-brand">knowledge</div>
                <div class="login-brand-sub">企业知识库 · 检索评测平台</div>
              </div>
            </div>
            <h1 class="login-title">创建账号</h1>
            <p class="login-sub">注册一个 knowledge 平台账号</p>
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              :hide-required-asterisk="true"
              class="login-form"
            >
              <el-form-item prop="username" label="用户名" class="login-item">
                <el-input
                  v-model="registerForm.username"
                  class="login-input"
                  placeholder="3-32 位用户名"
                  autocomplete="username"
                  :tabindex="cardSide === 'register' ? 0 : -1"
                />
              </el-form-item>
              <el-form-item prop="password" label="密码" class="login-item">
                <el-input
                  v-model="registerForm.password"
                  class="login-input"
                  type="password"
                  placeholder="6-64 位密码"
                  show-password
                  autocomplete="new-password"
                  :tabindex="cardSide === 'register' ? 0 : -1"
                />
              </el-form-item>
              <el-form-item prop="confirmPassword" label="确认密码" class="login-item">
                <el-input
                  v-model="registerForm.confirmPassword"
                  class="login-input"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                  autocomplete="new-password"
                  :tabindex="cardSide === 'register' ? 0 : -1"
                />
              </el-form-item>
              <el-button
                class="login-button"
                :loading="loading"
                :tabindex="cardSide === 'register' ? 0 : -1"
                @click="handleRegister"
              >
                注 册
              </el-button>
            </el-form>
            <div class="login-switch">
              <span class="login-switch-text">已有账号？</span>
              <button
                class="login-switch-link"
                type="button"
                :tabindex="cardSide === 'register' ? 0 : -1"
                @click="switchMode('login')"
              >
                直接登录
              </button>
            </div>
            <div class="login-foot">knowledge · 企业内部知识管理平台</div>
          </div>
        </div>
      </div>
    </main>
    <div class="login-status">
      <span class="login-dot"></span>
      系统运行正常
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { getToken, addUser } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

// 登录页：登录 / 注册各为一张独立卡片，互切时绕水平轴竖向翻转
const router = useRouter();
const authStore = useAuthStore();

// 翻转时长（ms）
const FLIP_DURATION = 520;
// 卡片高度下限与视口余量：避免容器塌到 90° 侧棱时页面跳动
const MIN_CARD_HEIGHT = 520;
const VIEWPORT_GAP = 96;

const mode = ref<'login' | 'register'>('login');
// half：翻转进度，0 = 登录面朝前，1 = 注册面朝前
const half = ref(0);
const flipping = ref(false);

const formRef = ref<FormInstance>();
const registerFormRef = ref<FormInstance>();
const flipRef = ref<HTMLElement>();
const loginCard = ref<HTMLElement>();
const registerCard = ref<HTMLElement>();
const loading = ref(false);

let frame = 0;
let startedAt = 0;
// 本次翻转的起点与终点：回登录是 1 → 0，不是 0 → 1
let fromFlip = 0;
let toFlip = 1;
let resizeObserver: ResizeObserver | undefined;

const cardSide = computed<'login' | 'register'>(() => (half.value > 0.5 ? 'register' : 'login'));

const form = reactive({
  account: '',
  password: '',
  remember: true,
});

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
});

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度须为 3-32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

// 回弹缓动：1-(1-t)^4，末段收得干净、不拖尾
function easeFlip(t: number): number {
  return 1 - Math.pow(1 - t, 4);
}

// 全局限幅：容器高度不越过视口，避免溢出
function clampHeight(value: number): number {
  const max = Math.max(280, window.innerHeight - VIEWPORT_GAP);
  return Math.round(Math.min(Math.max(value, MIN_CARD_HEIGHT), max));
}

// 逐帧只做两件事：写进度变量、按进度算高光/投影强度。
// 两张脸的 rotateX、透明度、缩放、阴影全部由这两个变量推导，不存在两份状态互相打架。
function paintFlip(value: number): void {
  half.value = value;
  const stage = flipRef.value;
  if (!stage) {
    return;
  }
  const angle = value * Math.PI;
  stage.style.setProperty('--flip', value.toFixed(4));
  // --edge：贴到 90° 侧棱时达到峰值，驱动缩放、投影与高光扫过
  stage.style.setProperty('--edge', Math.sin(angle).toFixed(4));
}

// 翻转期间两张脸都要可见且满不透明：正面/背面由 backface-visibility 负责，
// 不看角度算透明度，就不会出现两张脸各半透明叠在一起的镜像重影。
// 同时关掉毛玻璃（transform 祖先上的 backdrop-filter 在 Chromium 里会让整卡不渲染）
function showBothFaces(active: boolean): void {
  for (const el of [loginCard.value, registerCard.value]) {
    if (!el) {
      continue;
    }
    const face = el.querySelector<HTMLElement>('.login-face');
    if (active) {
      el.style.setProperty('opacity', '1');
      el.style.setProperty('visibility', 'visible');
      face?.classList.add('no-frost');
    } else {
      el.style.removeProperty('opacity');
      el.style.removeProperty('visibility');
      face?.classList.remove('no-frost');
    }
  }
}

// 容器高度显式写成像素（CSS transition 负责过渡）：
// 逐帧只写变量、不读布局，避免「写样式 → 读布局」交替触发强制重排
function syncHeight(): void {
  const stage = flipRef.value;
  const active = mode.value === 'login' ? loginCard.value : registerCard.value;
  if (!stage || !active) {
    return;
  }
  const height = clampHeight(active.offsetHeight);
  stage.style.height = `${height}px`;
  stage.style.setProperty('--h', `${height}px`);
}

function setFlipping(active: boolean): void {
  flipping.value = active;
  showBothFaces(active);
}

function tick(now: number): void {
  const progress = Math.min(1, (now - startedAt) / FLIP_DURATION);
  paintFlip(fromFlip + (toFlip - fromFlip) * easeFlip(progress));
  if (progress < 1) {
    frame = requestAnimationFrame(tick);
    return;
  }
  frame = 0;
  paintFlip(toFlip);
  setFlipping(false);
}

// 把 --flip 从当前进度推到目标值；减动效时直接落位
function runFlip(target: 'login' | 'register'): void {
  fromFlip = half.value;
  toFlip = target === 'register' ? 1 : 0;
  setFlipping(true);
  paintFlip(fromFlip);

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    paintFlip(toFlip);
    setFlipping(false);
    return;
  }
  startedAt = performance.now();
  frame = requestAnimationFrame(tick);
}

// 切换卡片：竖向翻转。正向（去注册）0 → 1，反向（回登录）1 → 0，
// 方向必须跟着目标走，否则回到登录时会被推到 1 又落回注册卡
function switchMode(next: 'login' | 'register'): void {
  if (flipping.value || next === mode.value) {
    return;
  }
  // 清掉离开那张卡的校验残留，回来时是干净的
  const leaving = next === 'login' ? registerFormRef.value : formRef.value;
  leaving?.clearValidate();
  mode.value = next;
  runFlip(next);
}

// 校验表单并调用登录接口；成功后持久化令牌并进入工作台
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  try {
    const loginResult = await getToken({ username: form.account, password: form.password });
    authStore.setToken(loginResult.token, form.remember);
    ElMessage.success('登录成功');
    await router.push('/knowledge-base');
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    loading.value = false;
  }
}

// 校验注册表单 → 注册 → 翻回登录卡并带好账号，由用户自己登录
async function handleRegister(): Promise<void> {
  const valid = await registerFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  try {
    await addUser({ username: registerForm.username, password: registerForm.password });
    ElMessage.success('注册成功，请登录');
    // 带回账号、清掉密码，避免浏览器把注册密码当成登录凭据留存
    form.account = registerForm.username;
    form.password = '';
    registerFormRef.value?.clearValidate();
    mode.value = 'login';
    runFlip('login');
  } catch {
    // 失败提示已由接口层统一拦截处理
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await nextTick();
  resizeObserver = new ResizeObserver(() => syncHeight());
  // 两张卡都观察：表单校验提示出现/消失会改变卡片高度，容器要跟着走
  for (const el of [loginCard.value, registerCard.value]) {
    if (el) {
      resizeObserver.observe(el);
    }
  }
  window.addEventListener('resize', syncHeight);
  // 初始把 --flip / --edge 落到 DOM 上，之后逐帧读写都有确定的值
  paintFlip(half.value);
  syncHeight();
});

onBeforeUnmount(() => {
  if (frame) {
    cancelAnimationFrame(frame);
  }
  frame = 0;
  resizeObserver?.disconnect();
  window.removeEventListener('resize', syncHeight);
});
</script>

<style scoped lang="css">
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

/* 3D 舞台：--h 是卡片高度，--flip 是翻转进度（0=登录面朝前，1=注册面朝前），
   --edge 是侧棱强度（sin(π·--flip)，翻转中由 JS 逐帧写入，静止时为 0）。
   两张脸的旋转、缩放、阴影与面板高光全部由这三个变量推导，只有一个数据源。 */
.login-flip {
  --h: 623px;
  --flip: 0;
  --edge: 0;

  position: relative;
  width: min(428px, 100%);
  height: var(--h);
  perspective: 1700px;
  transition: height 0.3s cubic-bezier(0.2, 0.7, 0.3, 1);
  animation: login-rise 0.55s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}

.login-card {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100%;
  transform-origin: 50% 50%;
  transform: translate(-50%, -50%);
  backface-visibility: hidden;
}

/* 静止态：正面卡可见可交互，背面卡退到后面待命 */
.login-card-back {
  visibility: hidden;
  opacity: 0;
  pointer-events: none;
}

/* 登录面：0° → -180°，自上而下翻走 */
.login-flip > .login-card:first-child {
  transform: translate(-50%, -50%) rotateX(calc(var(--flip) * -180deg))
    scale(calc(1 - var(--edge) * 0.03));
  filter: drop-shadow(0 calc(var(--edge) * 30px) calc(var(--edge) * 55px) rgb(0 0 0 / 60%));
}

/* 注册面：180° → 0°，自下而上翻入 */
.login-flip > .login-card:last-child {
  transform: translate(-50%, -50%) rotateX(calc(180deg - var(--flip) * 180deg))
    scale(calc(1 - var(--edge) * 0.03));
  filter: drop-shadow(0 calc(var(--edge) * 30px) calc(var(--edge) * 55px) rgb(0 0 0 / 60%));
}

/* 翻转中两张脸都要可见且满不透明，正面/背面交给 backface-visibility 判定，
   不看角度算透明度就不会出现两张脸叠影 */
.login-flip.flipping .login-card {
  visibility: visible;
  opacity: 1;
  pointer-events: none;
}

/* 背光层：静态用 backdrop-filter 做毛玻璃；
   翻转中由 JS 加 .no-frost 关掉，并同时给面板加一层实底保证对比度 */
.login-face {
  position: relative;
  padding: 44px 40px 36px;
  border: 1px solid var(--kb-line);
  border-radius: 20px;
  background: transparent;
  box-shadow: 0 30px 90px rgb(0 0 0 / 50%);
  overflow: hidden;
}

.login-face::before {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(180deg, rgb(255 255 255 / 6%), rgb(255 255 255 / 2.8%));
  backdrop-filter: blur(28px);
  content: '';
}

/* 翻转中给面板加实底并关掉毛玻璃：避让 Chromium 在 transform 祖先上的
   backdrop-filter 合成异常（会导致整张卡不渲染）。切换由 JS 加 .no-frost 完成 */
.login-face.no-frost {
  background: linear-gradient(180deg, rgb(13 18 30 / 96%), rgb(9 13 22 / 94%));
}

.login-face.no-frost::before {
  backdrop-filter: none;
}

/* 高光扫过：贴到 90° 侧棱时最亮，是「酷炫」的主要来源。
   直接吃父级继承下来的 --edge，单类名即可，不需要祖先状态选择器 */
.login-sheen {
  position: absolute;
  inset: 0;
  z-index: 2;
  border-radius: inherit;
  background: linear-gradient(
    180deg,
    rgb(52 211 153 / 16%),
    rgb(255 255 255 / 5%),
    transparent 62%
  );
  opacity: var(--edge);
  transition: opacity 0.26s ease;
  pointer-events: none;
}

.login-face > * {
  position: relative;
  z-index: 1;
}

.login-head {
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

.login-brand {
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

/* 表单项留出固定错误位，校验提示出现时不撑动卡片高度 */
.login-item {
  min-height: 82px;
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

.login-switch {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  margin-top: 22px;
  color: var(--kb-text-2);
  font-size: 13px;
}

.login-switch-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--kb-primary);
  font: inherit;
  font-weight: 600;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition:
    color 0.18s,
    opacity 0.18s;
}

.login-switch-link:hover {
  color: var(--kb-primary-2);
  opacity: 0.92;
}

.login-switch-link:focus-visible {
  border-radius: 4px;
  outline: 2px solid var(--kb-glow);
  outline-offset: 3px;
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
