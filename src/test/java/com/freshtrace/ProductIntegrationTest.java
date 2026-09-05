package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.CategoryUpdateDTO;
import com.freshtrace.product.dto.SpuAttributeDTO;
import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.product.dto.SpuUpdateDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void categoryCreateSuccess() throws Exception {
        mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("芒果", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("芒果"));
    }

    @Test
    void categoryGetSuccess() throws Exception {
        long id = createCategory("芒果");
        mockMvc.perform(get("/category/{id}", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("芒果"));
    }

    @Test
    void categoryUpdateSuccess() throws Exception {
        long id = createCategory("芒果");
        mockMvc.perform(put("/category/{id}", id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(categoryUpdate("荔枝", "http://icon/ly.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("荔枝"));
    }

    @Test
    void categoryDeleteSuccess() throws Exception {
        long id = createCategory("芒果");
        mockMvc.perform(delete("/category/{id}", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/category/{id}", id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30011));
    }

    @Test
    void categoryGetNotFound() throws Exception {
        mockMvc.perform(get("/category/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30011));
    }

    @Test
    void categoryDuplicateName() throws Exception {
        createCategory("芒果");
        mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("芒果", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30012));
    }

    @Test
    void categoryCreateParamInvalid() throws Exception {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName("");
        mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void categoryNormalUserForbidden() throws Exception {
        mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("芒果", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoryUnauthenticatedForbidden() throws Exception {
        mockMvc.perform(post("/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("芒果", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void categoryDeleteWithSpuFails() throws Exception {
        long categoryId = createCategory("芒果");
        createSpu(categoryId, "海南红心芒果");
        mockMvc.perform(delete("/category/{id}", categoryId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30013));
    }

    @Test
    void spuCreateSuccess() throws Exception {
        long categoryId = createCategory("芒果");
        mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spu(categoryId, "海南红心芒果"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.categoryId").value(categoryId));
    }

    @Test
    void spuDetailWithAttributes() throws Exception {
        long categoryId = createCategory("芒果");
        long spuId = createSpuWithAttributes(categoryId);

        MvcResult result = mockMvc.perform(get("/spu/{id}", spuId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tags[0]").value("有机"))
                .andExpect(jsonPath("$.data.attributes[0].attrName").value("规格"))
                .andExpect(jsonPath("$.data.attributes[0].attrValues[0]").value("5斤装"))
                .andExpect(jsonPath("$.data.attributes[0].attrValues[1]").value("10斤装"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"有机\"");
        assertThat(body).contains("\"5斤装\"");
    }

    @Test
    void spuUpdateSuccess() throws Exception {
        long categoryId = createCategory("芒果");
        long spuId = createSpu(categoryId, "海南红心芒果");

        mockMvc.perform(put("/spu/{id}", spuId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spuUpdate(categoryId, "海南贵妃芒果"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("海南贵妃芒果"));
    }

    @Test
    void spuDeleteSuccess() throws Exception {
        long categoryId = createCategory("芒果");
        long spuId = createSpu(categoryId, "海南红心芒果");
        mockMvc.perform(delete("/spu/{id}", spuId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/spu/{id}", spuId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30014));
    }

    @Test
    void spuCreateCategoryNotFound() throws Exception {
        mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spu(999999L, "海南红心芒果"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30015));
    }

    @Test
    void spuGetNotFound() throws Exception {
        mockMvc.perform(get("/spu/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30014));
    }

    @Test
    void spuNormalUserForbidden() throws Exception {
        long categoryId = createCategory("芒果");
        mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spu(categoryId, "海南红心芒果"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void endToEndCategorySpuDetail() throws Exception {
        long categoryId = createCategory("芒果");
        long spuId = createSpuWithAttributes(categoryId);

        mockMvc.perform(get("/spu/{id}", spuId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(spuId))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.name").value("海南红心芒果"))
                .andExpect(jsonPath("$.data.attributes.length()").value(2));
    }

    private String adminToken() {
        return jwtUtils.generateAccessToken(1L, 1);
    }

    private String userToken() {
        return jwtUtils.generateAccessToken(2L, 0);
    }

    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category(name, null))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createSpu(long categoryId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spu(categoryId, name))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createSpuWithAttributes(long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(spuWithAttributes(categoryId, "海南红心芒果"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private CategoryCreateDTO category(String name, String iconUrl) {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName(name);
        dto.setIconUrl(iconUrl);
        dto.setSortOrder(0);
        return dto;
    }

    private CategoryUpdateDTO categoryUpdate(String name, String iconUrl) {
        CategoryUpdateDTO dto = new CategoryUpdateDTO();
        dto.setName(name);
        dto.setIconUrl(iconUrl);
        dto.setSortOrder(0);
        return dto;
    }

    private SpuCreateDTO spu(long categoryId, String name) {
        SpuCreateDTO dto = new SpuCreateDTO();
        dto.setCategoryId(categoryId);
        dto.setName(name);
        dto.setStatus(1);
        return dto;
    }

    private SpuCreateDTO spuWithAttributes(long categoryId, String name) {
        SpuCreateDTO dto = spu(categoryId, name);
        dto.setTags(List.of("有机", "现摘"));

        SpuAttributeDTO attr1 = new SpuAttributeDTO();
        attr1.setAttrName("规格");
        attr1.setAttrValues(List.of("5斤装", "10斤装"));
        attr1.setSortOrder(0);

        SpuAttributeDTO attr2 = new SpuAttributeDTO();
        attr2.setAttrName("甜度");
        attr2.setAttrValues(List.of("高", "中"));
        attr2.setSortOrder(1);

        dto.setAttributes(List.of(attr1, attr2));
        return dto;
    }

    private SpuUpdateDTO spuUpdate(long categoryId, String name) {
        SpuUpdateDTO dto = new SpuUpdateDTO();
        dto.setCategoryId(categoryId);
        dto.setName(name);
        dto.setStatus(1);
        return dto;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
