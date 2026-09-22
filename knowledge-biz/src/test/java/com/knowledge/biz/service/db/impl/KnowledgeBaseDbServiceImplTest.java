package com.knowledge.biz.service.db.impl;

import com.knowledge.biz.mapper.KnowledgeBaseMapper;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DbService 单测：getActiveById 的对象级获取语义（存在返回 / 不存在抛 KB_NOT_FOUND）。
 *
 * @author cxxl
 */
class KnowledgeBaseDbServiceImplTest {

    private KnowledgeBaseMapper mapper;

    private KnowledgeBaseDbServiceImpl impl;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowledgeBaseMapper.class);
        impl = new KnowledgeBaseDbServiceImpl();
        ReflectionTestUtils.setField(impl, "baseMapper", mapper);
    }

    @Test
    void getActiveByIdWhenExistsShouldReturn() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setName("库1");
        when(mapper.selectById(1L)).thenReturn(kb);

        assertSame(kb, impl.getActiveById(1L));
    }

    @Test
    void getActiveByIdWhenAbsentShouldThrowNotFound() {
        when(mapper.selectById(2L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> impl.getActiveById(2L));
        assertEquals(ErrorCode.KB_NOT_FOUND, e.getErrorCode());
    }
}
