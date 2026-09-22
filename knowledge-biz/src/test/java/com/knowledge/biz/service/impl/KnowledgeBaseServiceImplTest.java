package com.knowledge.biz.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库管理应用服务单测。
 *
 * @author cxxl
 */
class KnowledgeBaseServiceImplTest {

    private KnowledgeBaseDbService knowledgeBaseDbService;

    private KbAuditLogDbService kbAuditLogDbService;

    private KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        knowledgeBaseDbService = mock(KnowledgeBaseDbService.class);
        kbAuditLogDbService = mock(KbAuditLogDbService.class);
        service = new KnowledgeBaseServiceImpl(knowledgeBaseDbService, kbAuditLogDbService);
    }

    private KnowledgeBase kb(long id, int status) {
        KnowledgeBase k = new KnowledgeBase();
        k.setId(id);
        k.setName("库" + id);
        k.setStatus(status);
        return k;
    }

    /** 默认知识库（defaultFlag=1，固定不可停用/删除） */
    private KnowledgeBase kbDefault(long id) {
        KnowledgeBase k = kb(id, 1);
        k.setDefaultFlag(1);
        return k;
    }

    @Test
    void createShouldSaveActiveAndAuditCreate() {
        KnowledgeBaseCreateDto dto = new KnowledgeBaseCreateDto();
        dto.setName("招投标知识库");
        dto.setDescription("招投标场景");
        doAnswer(inv -> {
            KnowledgeBase k = inv.getArgument(0);
            k.setId(1L);
            return true;
        }).when(knowledgeBaseDbService).save(any(KnowledgeBase.class));

        Long id = service.create(dto);

        assertEquals(1L, id);
        ArgumentCaptor<KnowledgeBase> kbCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseDbService).save(kbCaptor.capture());
        assertEquals(KnowledgeBaseStatus.ACTIVE.getCode(), kbCaptor.getValue().getStatus());
        assertEquals("招投标知识库", kbCaptor.getValue().getName());

        verify(kbAuditLogDbService).saveAudit(
                eq(AuditActionType.CREATE), eq("KNOWLEDGE_BASE"), eq(1L), isNull(), anyString());
    }

    @Test
    void detailWhenAbsentShouldThrowNotFound() {
        when(knowledgeBaseDbService.getActiveById(9L)).thenThrow(new KnowledgeException(ErrorCode.KB_NOT_FOUND));
        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.detail(9L));
        assertEquals(ErrorCode.KB_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void detailShouldMapToVO() {
        KnowledgeBase k = kb(2L, 1);
        k.setDescription("描述");
        when(knowledgeBaseDbService.getActiveById(2L)).thenReturn(k);

        KnowledgeBaseVO vo = service.detail(2L);

        assertEquals(2L, vo.getId());
        assertEquals("库2", vo.getName());
        assertEquals("描述", vo.getDescription());
        assertEquals(1, vo.getStatus());
    }

    @Test
    void detailShouldMapDefaultFlag() {
        when(knowledgeBaseDbService.getActiveById(2L)).thenReturn(kbDefault(2L));

        KnowledgeBaseVO vo = service.detail(2L);

        assertEquals(1, vo.getDefaultFlag());
    }

    @Test
    void updateShouldChangeNameAndAudit() {
        KnowledgeBase k = kb(3L, 1);
        when(knowledgeBaseDbService.getActiveById(3L)).thenReturn(k);
        KnowledgeBaseUpdateDto dto = new KnowledgeBaseUpdateDto();
        dto.setId(3L);
        dto.setName("新名称");
        dto.setDescription("新描述");

        service.update(dto);

        ArgumentCaptor<KnowledgeBase> cap = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseDbService).updateById(cap.capture());
        assertEquals("新名称", cap.getValue().getName());
        verify(kbAuditLogDbService).saveAudit(
                eq(AuditActionType.UPDATE), eq("KNOWLEDGE_BASE"), eq(3L), anyString(), anyString());
    }

    @Test
    void disableOnActiveShouldUpdateStatusAndAudit() {
        when(knowledgeBaseDbService.getActiveById(4L)).thenReturn(kb(4L, 1));

        service.disable(4L);

        ArgumentCaptor<KnowledgeBase> cap = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseDbService).updateById(cap.capture());
        assertEquals(0, cap.getValue().getStatus());
        verify(kbAuditLogDbService).saveAudit(
                eq(AuditActionType.DISABLE), eq("KNOWLEDGE_BASE"), eq(4L), anyString(), anyString());
    }

    @Test
    void disableOnDisabledShouldThrowAndNotUpdate() {
        when(knowledgeBaseDbService.getActiveById(4L)).thenReturn(kb(4L, 0));

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.disable(4L));

        assertEquals(ErrorCode.KB_STATUS_ILLEGAL, e.getErrorCode());
        verify(knowledgeBaseDbService, never()).updateById(any());
    }

    @Test
    void disableDefaultKbShouldThrowAndNotUpdate() {
        when(knowledgeBaseDbService.getActiveById(4L)).thenReturn(kbDefault(4L));

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.disable(4L));

        assertEquals(ErrorCode.KB_STATUS_ILLEGAL, e.getErrorCode());
        assertEquals("默认知识库不可停用或删除", e.getMessage());
        verify(knowledgeBaseDbService, never()).updateById(any());
    }

    @Test
    void enableOnDisabledShouldUpdateStatus() {
        when(knowledgeBaseDbService.getActiveById(5L)).thenReturn(kb(5L, 0));

        service.enable(5L);

        ArgumentCaptor<KnowledgeBase> cap = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseDbService).updateById(cap.capture());
        assertEquals(1, cap.getValue().getStatus());
        verify(kbAuditLogDbService).saveAudit(
                eq(AuditActionType.ENABLE), eq("KNOWLEDGE_BASE"), eq(5L), anyString(), anyString());
    }

    @Test
    void deleteShouldLogicDeleteAndAudit() {
        when(knowledgeBaseDbService.getActiveById(6L)).thenReturn(kb(6L, 1));

        service.delete(6L);

        verify(knowledgeBaseDbService).removeById(6L);
        verify(kbAuditLogDbService).saveAudit(
                eq(AuditActionType.DELETE), eq("KNOWLEDGE_BASE"), eq(6L), anyString(), isNull());
    }

    @Test
    void deleteDefaultKbShouldThrowAndNotRemove() {
        when(knowledgeBaseDbService.getActiveById(6L)).thenReturn(kbDefault(6L));

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.delete(6L));

        assertEquals(ErrorCode.KB_STATUS_ILLEGAL, e.getErrorCode());
        assertEquals("默认知识库不可停用或删除", e.getMessage());
        verify(knowledgeBaseDbService, never()).removeById(any());
    }

    @Test
    void pageShouldReturnMappedRecords() {
        Page<KnowledgeBase> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(kb(7L, 1)));
        when(knowledgeBaseDbService.pageByName(1L, 10L, "库7")).thenReturn(page);

        IPage<KnowledgeBaseVO> result = service.page(1, 10, "库7");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("库7", result.getRecords().getFirst().getName());
    }
}
