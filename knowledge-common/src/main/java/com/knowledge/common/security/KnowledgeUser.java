package com.knowledge.common.security;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户模型。
 *
 * @author cxxl
 */
@Data
public class KnowledgeUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录用户名 */
    private String username;
}
