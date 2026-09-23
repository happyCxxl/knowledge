package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.dto.request.strategy.StrategyVersionCreateDto;
import com.knowledge.common.dto.request.strategy.StrategyVersionUpdateDto;
import com.knowledge.common.dto.response.strategy.StrategyVersionVO;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 策略版本服务单测：类型白名单 / 列表（启用中 vs 全部）/ 创建 / 编辑=复制新版本 / 启停 /
 * 删除（有绑定引用禁删）/ CHUNK 配置校验。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class StrategyVersionServiceImplTest {

    @Mock
    private KbPipelineStrategyVersionDbService strategyVersionDbService;
    @Mock
    private KbStrategyBindingDbService strategyBindingDbService;

    private StrategyVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyVersionServiceImpl(strategyVersionDbService, strategyBindingDbService);
    }

    private KbPipelineStrategyVersion row(Long id, String type, String name, String version, String status) {
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setId(id);
        row.setType(type);
        row.setName(name);
        row.setVersion(version);
        row.setConfigSnapshot("{\"routes\":{}}");
        row.setStatus(status);
        return row;
    }

    private StrategyVersionCreateDto createDto() {
        StrategyVersionCreateDto dto = new StrategyVersionCreateDto();
        dto.setType("CHUNK");
        dto.setName("chunk-hybrid");
        dto.setVersion("v2");
        dto.setConfigSnapshot("{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\"}}}");
        return dto;
    }

    private StrategyVersionUpdateDto updateDto() {
        StrategyVersionUpdateDto dto = new StrategyVersionUpdateDto();
        dto.setName("chunk-strict");
        dto.setVersion("v2");
        dto.setConfigSnapshot("{\"routes\":{\"body\":{\"algorithm\":\"title-boundary\"}}}");
        return dto;
    }

    /** CHUNK 策略创建 DTO：routes/pipeline 配置结构 */
    private StrategyVersionCreateDto chunkDto(String configSnapshot) {
        StrategyVersionCreateDto dto = new StrategyVersionCreateDto();
        dto.setType("CHUNK");
        dto.setName("chunk-hybrid");
        dto.setVersion("v1");
        dto.setConfigSnapshot(configSnapshot);
        return dto;
    }

    @Test
    void listEnabledShouldReturnMappedVersions() {
        when(strategyVersionDbService.listEnabledByType("CHUNK"))
                .thenReturn(List.of(row(9L, "CHUNK", "chunk-hybrid", "v2", "ACTIVE")));

        List<StrategyVersionVO> vos = service.list("CHUNK", false);

        assertEquals(1, vos.size());
        assertEquals(9L, vos.getFirst().getId());
        assertEquals("ACTIVE", vos.getFirst().getStatus());
        verify(strategyVersionDbService, never()).listByType(any());
    }

    @Test
    void listWithIncludeInactiveShouldReturnAll() {
        when(strategyVersionDbService.listByType("CHUNK"))
                .thenReturn(List.of(row(10L, "CHUNK", "chunk-hybrid", "v1", "INACTIVE")));

        List<StrategyVersionVO> vos = service.list("CHUNK", true);

        assertEquals(1, vos.size());
        assertEquals("INACTIVE", vos.getFirst().getStatus());
        verify(strategyVersionDbService, never()).listEnabledByType(any());
    }

    @Test
    void listWithUnknownTypeShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.list("PREPROCESS", false));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(strategyVersionDbService, never()).listEnabledByType(any());
    }

    @Test
    void listWithBlankTypeShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.list("  ", false));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(strategyVersionDbService, never()).listEnabledByType(any());
    }

    @Test
    void createShouldSaveAsActive() {
        when(strategyVersionDbService.save(any(KbPipelineStrategyVersion.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineStrategyVersion.class).setId(11L);
            return true;
        });

        StrategyVersionVO vo = service.create(createDto());

        assertEquals(11L, vo.getId());
        assertEquals("ACTIVE", vo.getStatus());
    }

    @Test
    void createWithUnknownTypeShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDtoWithType("INDEX")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(strategyVersionDbService, never()).save(any());
    }

    @Test
    void createDuplicateKeyShouldReject() {
        when(strategyVersionDbService.save(any(KbPipelineStrategyVersion.class)))
                .thenThrow(new DuplicateKeyException("uk_type_name_version"));

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.create(createDto()));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("同环节同名版本已存在", e.getMessage());
    }

    @Test
    void updateShouldCopyAsNewVersion() {
        // 策略版本行不可变：编辑 = 复制新行（旧行原样保留，新行按 dto 注册且默认 ACTIVE）
        KbPipelineStrategyVersion existing = row(9L, "CHUNK", "chunk-hybrid", "v1", "INACTIVE");
        when(strategyVersionDbService.getById(9L)).thenReturn(existing);

        StrategyVersionVO vo = service.update(9L, updateDto());

        assertEquals("chunk-strict", vo.getName());
        assertEquals("v2", vo.getVersion());
        assertEquals("{\"routes\":{\"body\":{\"algorithm\":\"title-boundary\"}}}", vo.getConfigSnapshot());
        assertEquals("ACTIVE", vo.getStatus());
        ArgumentCaptor<KbPipelineStrategyVersion> captor = ArgumentCaptor.forClass(KbPipelineStrategyVersion.class);
        verify(strategyVersionDbService).save(captor.capture());
        assertEquals("CHUNK", captor.getValue().getType());
        // 旧行未被改动
        verify(strategyVersionDbService, never()).updateById(any(KbPipelineStrategyVersion.class));
        assertEquals("chunk-hybrid", existing.getName());
        assertEquals("v1", existing.getVersion());
        assertEquals("INACTIVE", existing.getStatus());
    }

    @Test
    void updateNotFoundShouldReject() {
        when(strategyVersionDbService.getById(9L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.update(9L, updateDto()));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
        verify(strategyVersionDbService, never()).save(any());
        verify(strategyVersionDbService, never()).updateById(any());
    }

    @Test
    void enableShouldSetActive() {
        when(strategyVersionDbService.getById(9L))
                .thenReturn(row(9L, "CHUNK", "chunk-hybrid", "v1", "INACTIVE"));

        StrategyVersionVO vo = service.enable(9L);

        assertEquals("ACTIVE", vo.getStatus());
        verify(strategyVersionDbService).updateById(any(KbPipelineStrategyVersion.class));
    }

    @Test
    void disableShouldSetInactive() {
        when(strategyVersionDbService.getById(9L))
                .thenReturn(row(9L, "CHUNK", "chunk-hybrid", "v1", "ACTIVE"));

        StrategyVersionVO vo = service.disable(9L);

        assertEquals("INACTIVE", vo.getStatus());
        verify(strategyVersionDbService).updateById(any(KbPipelineStrategyVersion.class));
    }

    @Test
    void enableNotFoundShouldReject() {
        when(strategyVersionDbService.getById(9L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.enable(9L));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
        verify(strategyVersionDbService, never()).updateById(any());
    }

    @Test
    void deleteShouldRemoveWhenNoBindingReference() {
        when(strategyVersionDbService.getById(9L))
                .thenReturn(row(9L, "CHUNK", "chunk-hybrid", "v1", "ACTIVE"));
        when(strategyBindingDbService.existsByStrategyVersionId(9L)).thenReturn(false);
        when(strategyVersionDbService.removeById((Serializable) 9L)).thenReturn(true);

        assertTrue(service.delete(9L));
        verify(strategyVersionDbService).removeById((Serializable) 9L);
    }

    @Test
    void deleteShouldRejectWhenBoundByKnowledgeBase() {
        // 策略版本行不可变：有绑定引用禁物理删除（停用代替删除）
        when(strategyVersionDbService.getById(9L))
                .thenReturn(row(9L, "CHUNK", "chunk-hybrid", "v1", "ACTIVE"));
        when(strategyBindingDbService.existsByStrategyVersionId(9L)).thenReturn(true);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.delete(9L));
        assertEquals(ErrorCode.STRATEGY_BOUND_DELETE_FORBIDDEN, e.getErrorCode());
        verify(strategyVersionDbService, never()).removeById(any(Serializable.class));
    }

    @Test
    void deleteNotFoundShouldReject() {
        when(strategyVersionDbService.getById(9L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.delete(9L));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
        verify(strategyVersionDbService, never()).removeById(any(Serializable.class));
    }

    // ---------------- CHUNK 配置校验（结构 / 支持集 / 参数范围 / 不变量 / 互斥） ----------------

    @Test
    void createChunkWithValidConfigShouldSave() {
        when(strategyVersionDbService.save(any(KbPipelineStrategyVersion.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineStrategyVersion.class).setId(12L);
            return true;
        });

        StrategyVersionVO vo = service.create(chunkDto(
                "{\"routes\":{\"body\":{\"algorithm\":\"structure-hybrid\","
                        + "\"params\":{\"targetMaxLen\":\"800\",\"softMaxLen\":\"1600\"}}},"
                        + "\"pipeline\":{\"titleInContent\":\"ON\",\"minMergeLen\":\"300\"}}"));

        assertEquals(12L, vo.getId());
        assertEquals("chunk-hybrid", vo.getName());
    }

    @Test
    void createChunkWithInvalidJsonShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDto("{not-json")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("配置不是合法 JSON", e.getMessage());
        verify(strategyVersionDbService, never()).save(any());
    }

    @Test
    void createChunkWithUnsupportedAlgorithmShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDto("{\"routes\":{\"body\":{\"algorithm\":\"semantic\"}}}")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("算法尚未支持: body.semantic", e.getMessage());
        verify(strategyVersionDbService, never()).save(any());
    }

    @Test
    void createChunkWithParamOutOfRangeShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDto(
                        "{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\","
                                + "\"params\":{\"targetMaxLen\":\"99999\"}}}}")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("参数越界: body.targetMaxLen（允许 100~5000）", e.getMessage());
        verify(strategyVersionDbService, never()).save(any());
    }

    @Test
    void createChunkWithSoftBelowTargetShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDto(
                        "{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\","
                                + "\"params\":{\"targetMaxLen\":\"800\",\"softMaxLen\":\"600\"}}}}")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("软上限必须 ≥ 目标片长上限（softMaxLen ≥ targetMaxLen）", e.getMessage());
        verify(strategyVersionDbService, never()).save(any());
    }

    @Test
    void createChunkWithTableInBodyFlowAndContextMergedShouldReject() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.create(chunkDto(
                        "{\"routes\":{\"table\":{\"algorithm\":\"context-merged\"}},"
                                + "\"pipeline\":{\"tableInBodyFlow\":\"ON\"}}")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        assertEquals("表格并入正文流与表+引导段落不可同时启用", e.getMessage());
        verify(strategyVersionDbService, never()).save(any());
    }

    private StrategyVersionCreateDto chunkDtoWithType(String type) {
        StrategyVersionCreateDto dto = chunkDto("{\"routes\":{}}");
        dto.setType(type);
        return dto;
    }
}
