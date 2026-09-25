import axios from 'axios';
import { ElMessage } from 'element-plus';

import type { R } from '@/types/response';

const TOKEN_KEY = 'knowledge-token';

/** 登录后要跳回的路径的暂存键（硬跳转会丢失当前 URL，用它带回来） */
const REDIRECT_KEY = 'knowledge-redirect';
const CODE_UNAUTHORIZED = 40101;
const CODE_SYSTEM_ERROR = 40500;

/** HTTP 401/403：兜底识别为未认证/无权限（后端正常情况下会把它们归一成 200 + R.code） */
const HTTP_UNAUTHORIZED = 401;
const HTTP_FORBIDDEN = 403;

// 统一请求实例：注入令牌；响应统一解包（非 0 码提示并拒绝，页面只处理成功分支）
export const http = axios.create({
  baseURL: '',
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** 清令牌并回登录页；已在登录页时不重复跳，避免刷新循环 */
function redirectToLogin(): void {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  if (window.location.pathname === '/login') {
    return;
  }
  // 记下来源：登录成功后由登录页读回
  sessionStorage.setItem(REDIRECT_KEY, window.location.pathname + window.location.search);
  window.location.href = '/login';
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as R<unknown>;
    if (body.code !== 0) {
      if (body.code === CODE_UNAUTHORIZED) {
        redirectToLogin();
      } else if (body.code === CODE_SYSTEM_ERROR) {
        ElMessage.error('系统异常，请稍后重试');
      } else {
        ElMessage.error(body.msg || '操作失败');
      }
      return Promise.reject(new Error(body.msg || '操作失败'));
    }
    response.data = body.data;
    return response;
  },
  (error) => {
    const status = error?.response?.status;
    // 令牌缺失/失效/被吊销时后端若直接返回 401/403，这里兜底回登录页；
    // 否则页面会停在拿不到数据的裸错误页，且因守卫认为"已登录"而无法回到登录页
    if (status === HTTP_UNAUTHORIZED || status === HTTP_FORBIDDEN) {
      redirectToLogin();
      return Promise.reject(error);
    }
    ElMessage.error('网络异常，请检查服务是否可用');
    return Promise.reject(error);
  },
);
