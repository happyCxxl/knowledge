package com.knowledge.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.dto.request.user.UserCreateRequest;
import com.knowledge.common.dto.request.user.UserUpdateRequest;
import com.knowledge.common.dto.response.user.UserVO;

/**
 * 用户服务：用户账号的查询与维护。
 *
 * <p>维护类操作（新增/编辑/删除）由管理员接口调用，服务层负责唯一性、自保护与
 * 「最后一个管理员」守卫。
 *
 * @author cxxl
 */
public interface UserService {

    /**
     * 分页查询用户（不含已删除；响应不含密码），支持用户名模糊、角色与状态过滤。
     *
     * @param current  当前页
     * @param size     每页条数
     * @param username 用户名（模糊查询，可空）
     * @param role     角色码值（可空）
     * @param status   状态（可空）
     * @return 用户分页结果
     */
    IPage<UserVO> pageUsers(long current, long size, String username, String role, Integer status);

    /**
     * 新增用户（管理员操作）。
     *
     * @param request 新增请求（用户名唯一、密码 BCrypt 加密）
     * @return 新用户主键
     */
    Long addUser(UserCreateRequest request);

    /**
     * 编辑用户（管理员操作）：用户名不可改，密码不传表示不重置。
     *
     * @param id      用户主键
     * @param request 编辑请求
     */
    void updateUser(Long id, UserUpdateRequest request);

    /**
     * 删除用户（逻辑删除，管理员操作）。
     *
     * @param id 用户主键
     */
    void deleteUser(Long id);
}
