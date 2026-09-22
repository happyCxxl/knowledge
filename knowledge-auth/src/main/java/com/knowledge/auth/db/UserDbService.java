package com.knowledge.auth.db;

import com.knowledge.common.domain.entity.User;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 用户数据访问服务（表：kb_user）。
 *
 * @author cxxl
 */
public interface UserDbService extends InfraDbService<User> {

    /**
     * 按用户名查启用状态用户。
     *
     * @param username 登录用户名
     * @return 用户实体；不存在或已停用/删除返回 null
     */
    User findActiveByUsername(String username);
}
