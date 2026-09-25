import axios from 'axios';
import { ElMessage } from 'element-plus';

import type { R } from '@/types/response';

const TOKEN_KEY = 'knowledge-token';
const CODE_UNAUTHORIZED = 40101;
const CODE_SYSTEM_ERROR = 40500;

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

http.interceptors.response.use(
  (response) => {
    const body = response.data as R<unknown>;
    if (body.code !== 0) {
      if (body.code === CODE_UNAUTHORIZED) {
        localStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(TOKEN_KEY);
        window.location.href = '/login';
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
    ElMessage.error('网络异常，请检查服务是否可用');
    return Promise.reject(error);
  },
);
