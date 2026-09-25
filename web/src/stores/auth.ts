import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const TOKEN_KEY = 'knowledge-token';

/** 角色码值：与后端 UserRole 对齐 */
export type UserRole = 'ADMIN' | 'USER';

function readToken(): string | null {
  return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
}

// 登录态：令牌持久化（记住我 → localStorage；否则 sessionStorage）
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken());

  const isLoggedIn = computed(() => Boolean(token.value));

  // 用户名与角色都取自 JWT 载荷（界面展示与菜单渲染用；真正的鉴权仍在后端）
  const username = computed(() => readClaim(token.value, 'username'));
  // 主键取自载荷 sub（后端签发时写入 userId），用于识别「当前账号自己」
  const userId = computed(() => readClaim(token.value, 'sub'));
  const role = computed<UserRole>(() =>
    readClaim(token.value, 'role') === 'ADMIN' ? 'ADMIN' : 'USER',
  );
  const isAdmin = computed(() => role.value === 'ADMIN');

  function setToken(value: string, remember: boolean): void {
    token.value = value;
    const target = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    target.setItem(TOKEN_KEY, value);
    other.removeItem(TOKEN_KEY);
  }

  function clearToken(): void {
    token.value = null;
    localStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
  }

  return { token, isLoggedIn, username, userId, role, isAdmin, setToken, clearToken };
});

/** 解析 JWT 载荷中的字符串声明；令牌缺失、结构异常或声明非字符串时返回 null */
function readClaim(value: string | null, key: string): string | null {
  const payload = value?.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes));
    if (parsed && typeof parsed === 'object' && key in parsed) {
      const claim = (parsed as Record<string, unknown>)[key];
      return typeof claim === 'string' ? claim : null;
    }
    return null;
  } catch {
    return null;
  }
}
