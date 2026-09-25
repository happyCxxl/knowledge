package com.knowledge.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.auth.db.UserDbService;
import com.knowledge.auth.service.UserService;
import com.knowledge.common.domain.entity.User;
import com.knowledge.common.dto.request.user.UserCreateRequest;
import com.knowledge.common.dto.request.user.UserUpdateRequest;
import com.knowledge.common.dto.response.user.UserVO;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.enums.user.UserRole;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import com.knowledge.common.security.audit.AuditEventPublisher;
import com.knowledge.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

/**
 * 用户服务实现。
 *
 * <p>管理员维护类操作的守卫：用户名唯一、角色合法、不得操作自身、
 * 不得让系统失去最后一个启用的管理员。
 *
 * @author cxxl
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 启用状态码值 */
    private static final int STATUS_ENABLED = 1;

    /** 停用状态码值 */
    private static final int STATUS_DISABLED = 0;

    /** 审计对象类型：用户账号 */
    private static final String AUDIT_OBJECT_TYPE = "KB_USER";

    private final UserDbService userDbService;

    private final PasswordEncoder passwordEncoder;

    private final AuditEventPublisher auditEventPublisher;

    @Override
    public IPage<UserVO> pageUsers(long current, long size, String username, String role, Integer status) {
        IPage<User> source = userDbService.pageByCondition(current, size, username, role, status);
        // 显式字段映射：密码等敏感字段不进入响应体
        Page<UserVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(UserServiceImpl::toUserVO).toList());
        return target;
    }

    @Override
    public Long addUser(UserCreateRequest request) {
        ThrowUtil.throwIf(userDbService.findActiveByUsername(request.getUsername()) != null,
                ErrorCode.USERNAME_EXISTS);
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(parseRole(request.getRole()).getCode());
        user.setStatus(request.getStatus() == null ? STATUS_ENABLED : parseStatus(request.getStatus()));
        userDbService.save(user);
        auditEventPublisher.publish(AuditActionType.USER_CREATE, AUDIT_OBJECT_TYPE, user.getId(),
                null, auditSummary(user));
        return user.getId();
    }

    @Override
    public void updateUser(Long id, UserUpdateRequest request) {
        User user = userDbService.getById(id);
        ThrowUtil.throwIf(user == null, ErrorCode.USER_NOT_FOUND);
        String beforeSummary = auditSummary(user);
        // 自保护：不允许改自己的角色或状态，避免管理员把自己锁在外面
        boolean self = isSelf(id);
        boolean roleChanged = request.getRole() != null;
        boolean statusChanged = request.getStatus() != null;
        ThrowUtil.throwIf(self && (roleChanged || statusChanged), ErrorCode.USER_SELF_OPERATION_FORBIDDEN);

        // 解析目标值并校验合法（非法码值/状态不落库）
        UserRole targetRole = roleChanged ? parseRole(request.getRole())
                : UserRole.of(user.getRole());
        Integer targetStatus = statusChanged ? parseStatus(request.getStatus()) : user.getStatus();

        // 变更后不再保留「启用的管理员」时，要求系统还有其他启用的管理员。
        // 三种路径都要堵：停用、降级为普通用户、删除（删除见 deleteUser）
        boolean losingEnabledAdmin = isEnabledAdmin(user)
                && (!isEnabledAdmin(targetRole.getCode(), targetStatus));
        ThrowUtil.throwIf(losingEnabledAdmin && countOtherEnabledAdmins(id) == 0,
                ErrorCode.LAST_ADMIN_FORBIDDEN);

        boolean passwordChanged = StrUtil.isNotBlank(request.getPassword());
        if (roleChanged) {
            user.setRole(targetRole.getCode());
        }
        if (statusChanged) {
            user.setStatus(targetStatus);
        }
        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        // 凭据或权限变更：递增令牌版本，让该账号已签发的令牌立即失效
        if (roleChanged || statusChanged || passwordChanged) {
            user.setTokenVersion(nextTokenVersion(user));
        }
        userDbService.updateById(user);
        auditEventPublisher.publish(resolveUpdateAction(roleChanged, statusChanged, targetStatus),
                AUDIT_OBJECT_TYPE, id, beforeSummary, auditSummary(user));
    }

    @Override
    public void deleteUser(Long id) {
        User user = userDbService.getById(id);
        ThrowUtil.throwIf(user == null, ErrorCode.USER_NOT_FOUND);
        // 自保护：不允许删除自己
        ThrowUtil.throwIf(isSelf(id), ErrorCode.USER_SELF_OPERATION_FORBIDDEN);
        // 删除启用中的管理员前确认系统仍有其他启用的管理员
        if (isEnabledAdmin(user)) {
            ThrowUtil.throwIf(countOtherEnabledAdmins(id) == 0, ErrorCode.LAST_ADMIN_FORBIDDEN);
        }
        userDbService.removeById(id);
        auditEventPublisher.publish(AuditActionType.USER_DELETE, AUDIT_OBJECT_TYPE, id,
                auditSummary(user), null);
    }

    /**
     * 更新动作归类：只改状态时记「启用 / 停用」，其余（改角色、重置密码、两者混合）记为更新。
     * 密码变更不落任何明文或哈希，摘要只体现角色与状态。
     */
    private AuditActionType resolveUpdateAction(boolean roleChanged, boolean statusChanged, Integer targetStatus) {
        if (!roleChanged && statusChanged) {
            return Integer.valueOf(STATUS_ENABLED).equals(targetStatus)
                    ? AuditActionType.USER_ENABLE
                    : AuditActionType.USER_DISABLE;
        }
        return AuditActionType.USER_UPDATE;
    }

    /** 审计摘要：只带出 id/用户名/角色/状态与令牌版本，绝不包含密码 */
    private String auditSummary(User user) {
        return JsonUtil.toJsonStr(Map.of(
                "id", user.getId() == null ? "" : String.valueOf(user.getId()),
                "username", StrUtil.nullToEmpty(user.getUsername()),
                "role", StrUtil.nullToEmpty(user.getRole()),
                "status", user.getStatus() == null ? "" : String.valueOf(user.getStatus()),
                "tokenVersion", user.getTokenVersion() == null ? "" : String.valueOf(user.getTokenVersion())));
    }

    /** 角色解析：非法码值直接拒绝，避免落库脏数据 */
    private UserRole parseRole(String code) {
        if (StrUtil.isBlank(code)) {
            return UserRole.USER;
        }
        boolean valid = Arrays.stream(UserRole.values())
                .anyMatch(role -> role.getCode().equalsIgnoreCase(code.trim()));
        ThrowUtil.throwIf(!valid, ErrorCode.ROLE_INVALID);
        return UserRole.of(code);
    }

    /** 状态校验：只接受 1 启用 / 0 停用，其他值拒绝落库 */
    private Integer parseStatus(Integer status) {
        ThrowUtil.throwIf(!Integer.valueOf(STATUS_ENABLED).equals(status)
                && !Integer.valueOf(STATUS_DISABLED).equals(status), ErrorCode.USER_STATUS_INVALID);
        return status;
    }

    /** 目标用户是否为启用状态的管理员 */
    private boolean isEnabledAdmin(User user) {
        return isEnabledAdmin(user.getRole(), user.getStatus());
    }

    /** 指定「角色 + 状态」组合是否构成一个启用的管理员 */
    private boolean isEnabledAdmin(String role, Integer status) {
        return UserRole.ADMIN.getCode().equalsIgnoreCase(role)
                && Integer.valueOf(STATUS_ENABLED).equals(status);
    }

    /** 除指定用户外，仍在启用状态的管理员数量 */
    private long countOtherEnabledAdmins(Long excludeId) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getRole, UserRole.ADMIN.getCode())
                .eq(User::getStatus, STATUS_ENABLED)
                .ne(User::getId, excludeId);
        return userDbService.count(queryWrapper);
    }

    /** 递增令牌版本：null 视为 0（与库表默认值一致） */
    private int nextTokenVersion(User user) {
        return (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
    }

    /** 当前登录用户是否就是目标用户 */
    private boolean isSelf(Long id) {
        KnowledgeUser current = SecurityUtils.getUser();
        return current != null && current.getId() != null && current.getId().equals(id);
    }

    /** 实体转视图对象：只带出可对外字段，避免密码泄漏 */
    private static UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
