package com.knowledge.auth.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.auth.service.UserService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.request.user.UserCreateRequest;
import com.knowledge.common.dto.request.user.UserUpdateRequest;
import com.knowledge.common.dto.response.user.UserVO;
import com.knowledge.common.security.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：平台账号的查询与维护。
 *
 * <p>类上标注 {@link AdminOnly}，本控制器所有接口仅管理员可访问。
 *
 * @author cxxl
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@AdminOnly
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "分页列表，支持用户名模糊查询与角色/状态过滤，"
            + "不含已删除；响应不含密码；仅管理员可访问")
    public R<IPage<UserVO>> pageUsers(
            @Parameter(description = "当前页") @RequestParam(value = "current", defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") long size,
            @Parameter(description = "用户名（模糊查询）") @RequestParam(value = "username", required = false)
            String username,
            @Parameter(description = "角色码值：ADMIN / USER") @RequestParam(value = "role", required = false)
            String role,
            @Parameter(description = "状态：1 启用 / 0 停用") @RequestParam(value = "status", required = false)
            Integer status) {
        return R.ok(userService.pageUsers(current, size, username, role, status));
    }

    @PostMapping("/add")
    @Operation(summary = "新增用户", description = "管理员新增账号；用户名全局唯一，密码 BCrypt 加密落库；仅管理员可访问")
    public R<String> addUser(@Valid @RequestBody UserCreateRequest request) {
        return R.ok(String.valueOf(userService.addUser(request)), "新增成功");
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑用户", description = "管理员编辑账号：用户名不可改，密码不传表示不重置，"
            + "角色/状态不传表示不变；不允许改自身角色或状态；仅管理员可访问")
    public R<Void> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        userService.updateUser(id, request);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "逻辑删除；不允许删除自身；不允许删除最后一个启用的管理员；仅管理员可访问")
    public R<Void> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id) {
        userService.deleteUser(id);
        return R.ok(null, "删除成功");
    }
}
