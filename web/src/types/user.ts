// 用户契约（与后端 UserVO/kb_user 对齐；响应不含密码）
export interface UserVO {
  /** 雪花 ID，后端按字符串下发防精度丢失 */
  id: string;
  username: string;
  /** 状态码值：1 启用 / 0 停用 */
  status: number;
  /** 角色码值：ADMIN 管理员 / USER 普通用户 */
  role: string | null;
  createBy: string | null;
  createTime: string | null;
  updateBy: string | null;
  updateTime: string | null;
}

/** 用户分页入参 */
export interface UserPageQuery {
  current: number;
  size: number;
  username?: string;
  /** 角色码值过滤：ADMIN / USER */
  role?: string;
  /** 状态过滤：1 启用 / 0 停用 */
  status?: number;
}

/** 新增用户入参（与后端 UserCreateRequest 对齐） */
export interface UserCreateRequest {
  username: string;
  password: string;
  role?: string;
  status?: number;
}

/** 编辑用户入参（与后端 UserUpdateRequest 对齐；用户名不可改，密码不传表示不重置） */
export interface UserUpdateRequest {
  password?: string;
  role?: string;
  status?: number;
}
