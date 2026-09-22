package com.knowledge.biz.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：插入/更新时填充逻辑删除标记与创建/更新留痕字段。
 *
 * @author cxxl
 */
@Configuration
public class KnowledgeMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String operator = currentOperator();
        this.strictInsertFill(metaObject, "delFlag", String.class, "0");
        this.strictInsertFill(metaObject, "createBy", String.class, operator);
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateBy", String.class, operator);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String operator = currentOperator();
        this.strictUpdateFill(metaObject, "updateBy", String.class, operator);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /** 当前操作人（无认证上下文返回 null） */
    private String currentOperator() {
        KnowledgeUser user = SecurityUtils.getUser();
        return user == null ? null : user.getUsername();
    }
}
