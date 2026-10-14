package com.knowledge.biz.controller;

import com.knowledge.biz.service.ModelAdminService;
import com.knowledge.biz.service.db.KbModelDbService;
import com.knowledge.biz.service.impl.ModelAdminServiceImpl;
import com.knowledge.common.annotation.AdminOnly;
import com.knowledge.common.domain.model.AuthType;
import com.knowledge.common.dto.request.model.ModelQueryDto;
import com.knowledge.common.dto.request.model.ModelSaveDto;
import com.knowledge.common.dto.request.model.ModelStatusDto;
import com.knowledge.common.dto.response.model.ModelItemVO;
import com.knowledge.common.dto.response.model.ModelProbeVO;
import com.knowledge.common.security.audit.AuditEventPublisher;
import com.knowledge.model.config.ModelVendorProperties;
import com.knowledge.model.invoke.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型管理接口路由测试：六条路径的映射与收参绑定、未定义的子路径不占用、每个方法都挂管理员权限注解，
 * 以及厂商清单这条只读接口的返回体里没有密钥字段与密钥取值。
 *
 * <p>用独立 MockMvc 装配单个 Controller：不加载数据源与容器，只验证 URL 到方法的映射与收参绑定；
 * 厂商清单那条走真实服务（数据访问与调用器打桩），好让"密钥不出接口"这条断言落在真实装配上。
 *
 * @author cxxl
 */
class ModelAdminControllerRoutingTest {

    /** 配置里那把密钥的真实取值：接口返回体里不应出现它 */
    private static final String SECRET = "sk-model-admin-secret";

    private ModelAdminService modelAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        modelAdminService = mock(ModelAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelAdminController(modelAdminService)).build();
    }

    @Test
    void listPathShouldRouteToServiceWithBoundQuery() throws Exception {
        when(modelAdminService.list(any())).thenReturn(List.of(new ModelItemVO()));

        mockMvc.perform(get("/admin/model/models")
                .param("capability", "CHAT")
                .param("status", "ENABLED")
                .param("keyword", "qwen")).andExpect(status().isOk());

        ModelQueryDto query = capturedListQuery();
        assertEquals("CHAT", query.getCapability());
        assertEquals("ENABLED", query.getStatus());
        assertEquals("qwen", query.getKeyword());
        verify(modelAdminService, never()).vendors();
    }

    @Test
    void createPathShouldRouteToServiceWithBody() throws Exception {
        when(modelAdminService.create(any())).thenReturn(new ModelItemVO());

        mockMvc.perform(post("/admin/model/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendorCode\":\"dashscope\",\"modelKey\":\"qwen-max\","
                        + "\"capabilities\":[\"CHAT\"],\"priceInPerM\":20,\"rpmLimit\":600}"))
                .andExpect(status().isOk());

        ModelSaveDto dto = capturedSave();
        assertEquals("dashscope", dto.getVendorCode());
        assertEquals("qwen-max", dto.getModelKey());
        assertEquals(List.of("CHAT"), dto.getCapabilities());
        assertEquals(600, dto.getRpmLimit());
    }

    @Test
    void updatePathShouldRouteToServiceWithIdAndBody() throws Exception {
        when(modelAdminService.update(any(), any())).thenReturn(new ModelItemVO());

        mockMvc.perform(put("/admin/model/models/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendorCode\":\"dashscope\",\"modelKey\":\"qwen-plus\",\"capabilities\":[\"CHAT\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<ModelSaveDto> dtoCaptor = ArgumentCaptor.forClass(ModelSaveDto.class);
        verify(modelAdminService).update(idCaptor.capture(), dtoCaptor.capture());
        assertEquals(5L, idCaptor.getValue());
        assertEquals("qwen-plus", dtoCaptor.getValue().getModelKey());
    }

    @Test
    void statusPathShouldRouteToServiceWithStatus() throws Exception {
        when(modelAdminService.updateStatus(any(), any())).thenReturn(new ModelItemVO());

        mockMvc.perform(patch("/admin/model/models/6/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}")).andExpect(status().isOk());

        ArgumentCaptor<ModelStatusDto> captor = ArgumentCaptor.forClass(ModelStatusDto.class);
        verify(modelAdminService).updateStatus(eq(6L), captor.capture());
        assertEquals("DISABLED", captor.getValue().getStatus());
    }

    @Test
    void probePathShouldRouteToService() throws Exception {
        when(modelAdminService.probe(any())).thenReturn(new ModelProbeVO());

        mockMvc.perform(post("/admin/model/models/7/probe")).andExpect(status().isOk());

        verify(modelAdminService).probe(7L);
        verify(modelAdminService, never()).create(any());
    }

    @Test
    void vendorsShouldBeReadOnlyAndHideCredentials() throws Exception {
        ModelAdminService realService = new ModelAdminServiceImpl(mock(KbModelDbService.class),
                vendorProperties(SECRET), mock(ModelInvoker.class), mock(AuditEventPublisher.class));
        MockMvc realMvc = MockMvcBuilders.standaloneSetup(new ModelAdminController(realService)).build();

        String body = realMvc.perform(get("/admin/model/vendors")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("\"code\":\"dashscope\""), body);
        assertTrue(body.contains("\"credentialConfigured\":true"), body);
        assertTrue(body.contains("阿里百炼"), body);
        assertFalse(body.contains(SECRET), body);
        assertFalse(body.contains("apiKey"), body);
        assertFalse(body.toLowerCase().contains("apikey"), body);
        assertFalse(body.contains("\"credential\":"), body);

        // 只读接口：没有新增与编辑的映射，写入方法回 405
        realMvc.perform(post("/admin/model/vendors")).andExpect(status().isMethodNotAllowed());
        realMvc.perform(put("/admin/model/vendors")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void credentialAndRoutePolicyPathsShouldNotBeMapped() throws Exception {
        mockMvc.perform(get("/admin/model/credentials")).andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/model/credentials")).andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/model/route-policies")).andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/model/models/1/rotate")).andExpect(status().isNotFound());
        mockMvc.perform(put("/admin/model/models")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(modelAdminService);
    }

    @Test
    void everyEndpointShouldRequireAdminRole() {
        for (String name : List.of("models", "create", "update", "updateStatus", "probe", "vendors")) {
            assertTrue(adminOnlyOf(name), name + " 必须挂 @AdminOnly");
        }
    }

    /** 方法名上是否挂了管理员权限注解 */
    private boolean adminOnlyOf(String methodName) {
        for (Method method : ModelAdminController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                assertNotNull(method.getAnnotation(AdminOnly.class), methodName);
                return true;
            }
        }
        return false;
    }

    /** 单厂商配置：密钥取值只在配置对象里，不下发到接口 */
    private ModelVendorProperties vendorProperties(String apiKey) {
        ModelVendorProperties.Vendor vendor = new ModelVendorProperties.Vendor();
        vendor.setAuthType(AuthType.BEARER);
        vendor.setApiKey(apiKey);
        Map<String, ModelVendorProperties.Vendor> vendors = new LinkedHashMap<>();
        vendors.put("dashscope", vendor);
        ModelVendorProperties properties = new ModelVendorProperties();
        properties.setVendors(vendors);
        return properties;
    }

    /** 取最近一次清单接口的收参 */
    private ModelQueryDto capturedListQuery() {
        ArgumentCaptor<ModelQueryDto> captor = ArgumentCaptor.forClass(ModelQueryDto.class);
        verify(modelAdminService).list(captor.capture());
        return captor.getValue();
    }

    /** 取最近一次新增接口的收参 */
    private ModelSaveDto capturedSave() {
        ArgumentCaptor<ModelSaveDto> captor = ArgumentCaptor.forClass(ModelSaveDto.class);
        verify(modelAdminService).create(captor.capture());
        return captor.getValue();
    }
}
