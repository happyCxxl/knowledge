package com.knowledge.biz.controller;

import com.knowledge.biz.service.ModelUsageService;
import com.knowledge.common.annotation.AdminOnly;
import com.knowledge.common.domain.model.calllog.ModelCallLogSummary;
import com.knowledge.common.domain.model.calllog.ModelCallLogView;
import com.knowledge.common.dto.request.model.ModelUsageQueryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型用量接口路由测试：概览与明细两条路径的映射与查询参数绑定，未定义的子路径不占用，
 * 两个方法都挂管理员权限注解。
 *
 * <p>用独立 MockMvc 装配单个 Controller：不加载数据源与容器，只验证 URL 到方法的映射与收参绑定。
 *
 * @author cxxl
 */
class ModelUsageControllerRoutingTest {

    private ModelUsageService modelUsageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        modelUsageService = mock(ModelUsageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelUsageController(modelUsageService)).build();
    }

    @Test
    void summaryPathShouldRouteToServiceWithBoundQuery() throws Exception {
        when(modelUsageService.summary(any())).thenReturn(new ModelCallLogSummary());

        mockMvc.perform(get("/admin/model/usage/summary")
                .param("from", "2026-10-01")
                .param("to", "2026-10-02")
                .param("capability", "CHAT")).andExpect(status().isOk());

        ModelUsageQueryDto query = capturedSummaryQuery();
        assertEquals("2026-10-01", query.getFrom());
        assertEquals("2026-10-02", query.getTo());
        assertEquals("CHAT", query.getCapability());
        verify(modelUsageService, never()).calls(any());
    }

    @Test
    void callsPathShouldRouteToServiceWithBoundQuery() throws Exception {
        when(modelUsageService.calls(any())).thenReturn(List.of(new ModelCallLogView()));

        mockMvc.perform(get("/admin/model/usage/calls")
                .param("from", "2026-10-01T00:00:00")
                .param("to", "2026-10-01T01:00:00")
                .param("model", "qwen-max")
                .param("routeReason", "OK")
                .param("status", "SUCCESS")
                .param("limit", "20")).andExpect(status().isOk());

        ModelUsageQueryDto query = capturedListQuery();
        assertEquals("qwen-max", query.getModel());
        assertEquals("OK", query.getRouteReason());
        assertEquals("SUCCESS", query.getStatus());
        assertEquals(20, query.getLimit());
        verify(modelUsageService, never()).summary(any());
    }

    @Test
    void undefinedPathsShouldNotBeMapped() throws Exception {
        mockMvc.perform(get("/admin/model/usage")).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/model/usage/calls/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/model/models")).andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/model/usage/summary")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(modelUsageService);
    }

    @Test
    void bothEndpointsShouldRequireAdminRole() throws Exception {
        assertAdminOnly("summary", ModelUsageQueryDto.class);
        assertAdminOnly("calls", ModelUsageQueryDto.class);
    }

    /** 取最近一次概览接口的收参 */
    private ModelUsageQueryDto capturedSummaryQuery() {
        ArgumentCaptor<ModelUsageQueryDto> captor = ArgumentCaptor.forClass(ModelUsageQueryDto.class);
        verify(modelUsageService).summary(captor.capture());
        return captor.getValue();
    }

    /** 取最近一次明细接口的收参 */
    private ModelUsageQueryDto capturedListQuery() {
        ArgumentCaptor<ModelUsageQueryDto> captor = ArgumentCaptor.forClass(ModelUsageQueryDto.class);
        verify(modelUsageService).calls(captor.capture());
        return captor.getValue();
    }

    /** 方法上必须挂管理员权限注解 */
    private void assertAdminOnly(String methodName, Class<?> parameterType) throws NoSuchMethodException {
        Method method = ModelUsageController.class.getMethod(methodName, parameterType);
        assertNotNull(method.getAnnotation(AdminOnly.class), methodName + " 必须挂 @AdminOnly");
    }
}
