package com.knowledge.auth.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.auth.db.UserDbService;
import com.knowledge.common.domain.entity.User;
import com.knowledge.auth.mapper.UserMapper;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class UserDbServiceImpl extends InfraDbServiceImpl<UserMapper, User> implements UserDbService {

    @Override
    public User findActiveByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username)
                .eq(User::getStatus, 1);
        return getOne(queryWrapper, false);
    }
}
