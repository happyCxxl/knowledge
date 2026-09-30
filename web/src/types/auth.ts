// 认证契约（与后端 LoginRequest/LoginVO/RegisterRequest 对齐）
export interface LoginRequest {
  username: string;
  password: string;
  /** 记住我：true 换长期令牌，false 换短期令牌 */
  remember: boolean;
}

/** 登录结果：角色与用户 ID 从令牌载荷解析，这里只带界面要用的资料字段 */
export interface LoginVO {
  token: string;
  displayName: string | null;
  email: string | null;
  phone: string | null;
}

export interface RegisterRequest {
  username: string;
  /** 真实姓名 */
  displayName: string;
  /** 邮箱（可空） */
  email?: string;
  /** 手机号（可空） */
  phone?: string;
  password: string;
}
