import { http } from './http';
import type { PageResult } from '@/types/response';
import type { UserCreateRequest, UserPageQuery, UserUpdateRequest, UserVO } from '@/types/user';

/** 分页查询用户（仅管理员；响应不含密码） */
export async function getUserPage(query: UserPageQuery): Promise<PageResult<UserVO>> {
  const response = await http.get<PageResult<UserVO>>('/user/page', { params: query });
  return response.data;
}

/** 新增用户（仅管理员） */
export async function addUserByAdmin(request: UserCreateRequest): Promise<string> {
  const response = await http.post<string>('/user/add', request);
  return response.data;
}

/** 编辑用户（仅管理员；用户名不可改，密码不传表示不重置） */
export async function updateUser(id: string, request: UserUpdateRequest): Promise<void> {
  await http.put<void>(`/user/${id}`, request);
}

/** 删除用户（仅管理员；逻辑删除） */
export async function deleteUser(id: string): Promise<void> {
  await http.delete<void>(`/user/${id}`);
}
