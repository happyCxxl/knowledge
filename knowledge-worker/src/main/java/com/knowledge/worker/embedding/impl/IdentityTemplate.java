package com.knowledge.worker.embedding.impl;

import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.worker.embedding.template.InputTemplatePort;
import org.springframework.stereotype.Component;

/**
 * 原样模板（一期唯一实现）：inputText = chunk.content，零改动。
 * 红线①"不改内容"的执行者——模板结构本期定全但行为一期原样（2026-09-07 用户拍板）。
 *
 * @author cxxl
 */
@Component
public class IdentityTemplate implements InputTemplatePort {

    @Override
    public String render(String template, Chunk chunk) {
        return chunk.getContent();
    }
}
