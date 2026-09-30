import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const TOKEN_KEY = 'knowledge-token';
const DISPLAY_NAME_KEY = 'knowledge-display-name';

/** 角色码值：与后端 UserRole 对齐 */
export type UserRole = 'ADMIN' | 'USER';

function readToken(): string | null {
  return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
}

/** 令牌当前所在的那一档存储；两边都没有时回落 localStorage */
function tokenStorage(): Storage {
  return localStorage.getItem(TOKEN_KEY) === null ? sessionStorage : localStorage;
}

/** 清掉两个存储里的令牌与姓名缓存 */
function clearStored(): void {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem(TOKEN_KEY);
    storage.removeItem(DISPLAY_NAME_KEY);
  }
}

/** 令牌是否可用：仅当存在、可解析、且未过期时才算已登录。
 *
 * <p>此前只判断"是否存在"，导致一个过期令牌会让守卫认为已登录：
 * 访问 /login 被踢回工作台，接口再因令牌失效而拒绝，用户被卡在错误页再也回不到登录页。
 * 这里对过期令牌直接清除，从根上断掉这个循环。
 */
function isTokenUsable(token: string | null): boolean {
  const expiresAt = readNumberClaim(token, 'exp');
  if (expiresAt === null) {
    return false;
  }
  // exp 是秒级时间戳（JWT 规范），换算成毫秒与当前时间比较
  return expiresAt * 1000 > Date.now();
}

/** 丢弃不可用令牌与其姓名缓存，避免"看似已登录" */
function dropUnusableToken(): string | null {
  const token = readToken();
  if (token === null || isTokenUsable(token)) {
    return token;
  }
  clearStored();
  return null;
}

// 登录态：令牌与真实姓名持久化（记住我 → localStorage；否则 sessionStorage）
export const useAuthStore = defineStore('auth', () => {
  const initialToken = dropUnusableToken();
  const token = ref<string | null>(initialToken);
  // 无可用令牌时不展示残留姓名
  const displayName = ref<string | null>(
    initialToken === null
      ? null
      : (localStorage.getItem(DISPLAY_NAME_KEY) ?? sessionStorage.getItem(DISPLAY_NAME_KEY)),
  );

  const isLoggedIn = computed(() => Boolean(token.value));

  // 用户名与角色都取自 JWT 载荷（界面展示与菜单渲染用；真正的鉴权仍在后端）
  const username = computed(() => readClaim(token.value, 'username'));
  // 主键取自载荷 sub（后端签发时写入 userId），用于识别「当前账号自己」
  const userId = computed(() => readClaim(token.value, 'sub'));
  const role = computed<UserRole>(() =>
    readClaim(token.value, 'role') === 'ADMIN' ? 'ADMIN' : 'USER',
  );
  const isAdmin = computed(() => role.value === 'ADMIN');

  /** 写入令牌与姓名：两者同存一档，避免"关了浏览器还剩一个名字" */
  function setToken(value: string, remember: boolean, name: string | null): void {
    token.value = value;
    displayName.value = name;
    const target = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    target.setItem(TOKEN_KEY, value);
    if (name) {
      target.setItem(DISPLAY_NAME_KEY, name);
    } else {
      target.removeItem(DISPLAY_NAME_KEY);
    }
    other.removeItem(TOKEN_KEY);
    other.removeItem(DISPLAY_NAME_KEY);
  }

  /** 更新本地展示的姓名（个人信息保存后调用，不必重新登录） */
  function setDisplayName(name: string | null): void {
    displayName.value = name;
    const target = tokenStorage();
    if (name) {
      target.setItem(DISPLAY_NAME_KEY, name);
    } else {
      target.removeItem(DISPLAY_NAME_KEY);
    }
  }

  /** 换发令牌（修改密码后）：写回令牌当前所在的那一档存储，不改变「记住我」的选择 */
  function replaceToken(value: string): void {
    token.value = value;
    tokenStorage().setItem(TOKEN_KEY, value);
  }

  function clearToken(): void {
    token.value = null;
    displayName.value = null;
    clearStored();
  }

  return {
    token,
    isLoggedIn,
    username,
    userId,
    role,
    isAdmin,
    displayName,
    setToken,
    setDisplayName,
    replaceToken,
    clearToken,
  };
});

/** 解析 JWT 载荷；令牌缺失或结构异常时返回 null */
function decodePayload(value: string | null): Record<string, unknown> | null {
  const payload = value?.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** 解析 JWT 载荷中的字符串声明；令牌缺失、结构异常或声明非字符串时返回 null */
function readClaim(value: string | null, key: string): string | null {
  const claim = decodePayload(value)?.[key];
  return typeof claim === 'string' ? claim : null;
}

/** 解析 JWT 载荷中的数字声明（exp 等）；缺失或非数字时返回 null */
function readNumberClaim(value: string | null, key: string): number | null {
  const claim = decodePayload(value)?.[key];
  return typeof claim === 'number' && Number.isFinite(claim) ? claim : null;
}
