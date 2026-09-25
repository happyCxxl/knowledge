// 认证契约（与后端 LoginRequest/LoginVO/RegisterRequest 对齐）
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginVO {
  token: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}
