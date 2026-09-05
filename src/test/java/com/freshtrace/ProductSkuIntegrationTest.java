package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductAttributeDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.ProductImageDTO;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.dto.ProductUpdateDTO;
import com.freshtrace.product.dto.SpuCreateDTO;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
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

import java.math.BigDecimal;
import java.util.List;

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
class ProductSkuIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Test
    void farmerCreateProductSuccess() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");

        mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreate(spuId, "海南红心芒果 5斤装", "49.90", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void farmerCreateProductSpuNotFound() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreate(999999L, "海南红心芒果 5斤装", "49.90", 100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30017));
    }

    @Test
    void normalUserCannotCreateProduct() throws Exception {
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreate(spuId, "海南红心芒果 5斤装", "49.90", 100))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreate(1L, "海南红心芒果 5斤装", "49.90", 100))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productCreateParamInvalid() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        ProductCreateDTO dto = productCreate(spuId, "海南红心芒果 5斤装", "49.90", 100);
        dto.setPrice(null);
        mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void farmerUpdateOwnProductSuccess() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        ProductUpdateDTO update = new ProductUpdateDTO();
        update.setSpuId(spuId);
        update.setTitle("海南红心芒果 10斤装");
        update.setPrice(new BigDecimal("89.90"));
        update.setStock(200);
        update.setVersion(0);

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("海南红心芒果 10斤装"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void farmerCannotUpdateOthersProduct() throws Exception {
        long farmerA = createFarmer("13800000001");
        long farmerB = createFarmer("13800000002");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerA, spuId, "海南红心芒果 5斤装");

        ProductUpdateDTO update = new ProductUpdateDTO();
        update.setSpuId(spuId);
        update.setTitle("海南红心芒果 10斤装");
        update.setPrice(new BigDecimal("89.90"));
        update.setStock(200);
        update.setVersion(0);

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30018));
    }

    @Test
    void productOptimisticLockConflict() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        ProductUpdateDTO update = new ProductUpdateDTO();
        update.setSpuId(spuId);
        update.setTitle("海南红心芒果 10斤装");
        update.setPrice(new BigDecimal("89.90"));
        update.setStock(200);
        update.setVersion(0);

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        ProductUpdateDTO stale = new ProductUpdateDTO();
        stale.setSpuId(spuId);
        stale.setTitle("海南红心芒果 20斤装");
        stale.setPrice(new BigDecimal("159.90"));
        stale.setStock(300);
        stale.setVersion(0);

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30022));
    }

    @Test
    void lifecycleLegalTransition() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        ProductLifecycleUpdateDTO dto = new ProductLifecycleUpdateDTO();
        dto.setLifecycle(1);
        mockMvc.perform(post("/product/{id}/lifecycle", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.lifecycle").value(1));
    }

    @Test
    void lifecycleIllegalTransition() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        ProductLifecycleUpdateDTO dto = new ProductLifecycleUpdateDTO();
        dto.setLifecycle(3);
        mockMvc.perform(post("/product/{id}/lifecycle", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30019));
    }

    @Test
    void adminAuditApprove() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void adminAuditReject() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(2, "图片不清晰"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(2))
                .andExpect(jsonPath("$.data.auditReason").value("图片不清晰"));
    }

    @Test
    void adminAuditRejectReasonRequired() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(2, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30021));
    }

    @Test
    void normalUserCannotAudit() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAudit() throws Exception {
        mockMvc.perform(post("/product/{id}/audit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditNotPendingFails() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30020));
    }

    @Test
    void productDetailComplete() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long categoryId = createCategory("芒果");
        long spuId = createSpu(categoryId, "海南红心芒果");
        long productId = createProductWithAttributes(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("海南红心芒果 5斤装"))
                .andExpect(jsonPath("$.data.spu.id").value(spuId))
                .andExpect(jsonPath("$.data.spu.name").value("海南红心芒果"))
                .andExpect(jsonPath("$.data.category.id").value(categoryId))
                .andExpect(jsonPath("$.data.category.name").value("芒果"))
                .andExpect(jsonPath("$.data.farmer.orchardName").value("测试果园"))
                .andExpect(jsonPath("$.data.attributes[0].attrName").value("规格"))
                .andExpect(jsonPath("$.data.attributes[0].attrValue").value("5斤装"))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value("http://img/1.png"))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.traceNodeCount").value(0));
    }

    @Test
    void endToEndCreateAuditDetail() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long categoryId = createCategory("芒果");
        long spuId = createSpu(categoryId, "海南红心芒果");
        long productId = createProductWithAttributes(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1))
                .andExpect(jsonPath("$.data.spu.id").value(spuId))
                .andExpect(jsonPath("$.data.category.id").value(categoryId))
                .andExpect(jsonPath("$.data.farmer.orchardName").value("测试果园"))
                .andExpect(jsonPath("$.data.attributes.length()").value(2))
                .andExpect(jsonPath("$.data.images.length()").value(1));
    }

    @Test
    void endToEndRejectThenEditResubmit() throws Exception {
        long farmerUserId = createFarmer("13800000001");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(2, "图片不清晰"))))
                .andExpect(status().isOk());

        ProductUpdateDTO update = new ProductUpdateDTO();
        update.setSpuId(spuId);
        update.setTitle("海南红心芒果 5斤装（新图）");
        update.setPrice(new BigDecimal("49.90"));
        update.setStock(100);
        update.setVersion(0);

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/product/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void approvedEditPriceKeepsApproved() throws Exception {
        long farmerUserId = createFarmer("13800000010");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 5斤装", "59.90", 100, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void approvedEditStockKeepsApproved() throws Exception {
        long farmerUserId = createFarmer("13800000011");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 5斤装", "49.90", 500, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void approvedEditTitleGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000012");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 10斤装", "49.90", 100, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void approvedEditSpuGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000013");
        long categoryId = createCategory("芒果");
        long spuId = createSpu(categoryId, "海南红心芒果");
        long otherSpuId = createSpu(categoryId, "海南贵妃芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(otherSpuId, "海南红心芒果 5斤装", "49.90", 100, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void approvedEditAttributeGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000014");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProductWithAttributes(farmerUserId, spuId, "海南红心芒果 5斤装");
        approve(productId);

        ProductUpdateDTO dto = updateDto(spuId, "海南红心芒果 5斤装", "49.90", 100, 0);
        ProductAttributeDTO attr = new ProductAttributeDTO();
        attr.setAttrName("规格");
        attr.setAttrValue("20斤装");
        attr.setExtraPrice(BigDecimal.ZERO);
        dto.setAttributes(List.of(attr));

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void approvedEditMainImageGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000015");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        ProductUpdateDTO dto = updateDto(spuId, "海南红心芒果 5斤装", "49.90", 100, 0);
        dto.setMainImage("http://img/main.png");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void approvedEditPriceAndTitleGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000016");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createAndApprove(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 10斤装", "59.90", 100, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void rejectedEditGoesPending() throws Exception {
        long farmerUserId = createFarmer("13800000017");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(2, "图片不清晰"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 5斤装", "49.90", 100, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void pendingEditNormalFieldKeepsPending() throws Exception {
        long farmerUserId = createFarmer("13800000018");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(put("/product/{id}", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateDto(spuId, "海南红心芒果 5斤装", "59.90", 200, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void lifecyclePresaleToRipe() throws Exception {
        long farmerUserId = createFarmer("13800000019");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
    }

    @Test
    void lifecycleRipeToOnSale() throws Exception {
        long farmerUserId = createFarmer("13800000020");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
        changeLifecycle(productId, farmerUserId, 3);
    }

    @Test
    void lifecycleOnSaleToSoldOut() throws Exception {
        long farmerUserId = createFarmer("13800000021");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
        changeLifecycle(productId, farmerUserId, 3);
        changeLifecycle(productId, farmerUserId, 4);
    }

    @Test
    void lifecyclePlantingToRipeIllegal() throws Exception {
        long farmerUserId = createFarmer("13800000022");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        assertLifecycleIllegal(productId, farmerUserId, 2);
    }

    @Test
    void lifecyclePresaleToOnSaleIllegal() throws Exception {
        long farmerUserId = createFarmer("13800000023");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        assertLifecycleIllegal(productId, farmerUserId, 3);
    }

    @Test
    void lifecycleRipeToSoldOutIllegal() throws Exception {
        long farmerUserId = createFarmer("13800000024");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
        assertLifecycleIllegal(productId, farmerUserId, 4);
    }

    @Test
    void cancelPreSaleSuccess() throws Exception {
        long farmerUserId = createFarmer("13800000025");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);

        mockMvc.perform(post("/product/{id}/cancel-pre-sale", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.lifecycle").value(0));
    }

    @Test
    void cancelPreSaleInvalidState() throws Exception {
        long farmerUserId = createFarmer("13800000026");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/cancel-pre-sale", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30019));
    }

    @Test
    void soldOutToOnSaleViaLifecycleFails() throws Exception {
        long farmerUserId = createFarmer("13800000027");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
        changeLifecycle(productId, farmerUserId, 3);
        changeLifecycle(productId, farmerUserId, 4);

        assertLifecycleIllegal(productId, farmerUserId, 3);
    }

    @Test
    void restockSuccess() throws Exception {
        long farmerUserId = createFarmer("13800000028");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");
        changeLifecycle(productId, farmerUserId, 1);
        changeLifecycle(productId, farmerUserId, 2);
        changeLifecycle(productId, farmerUserId, 3);
        changeLifecycle(productId, farmerUserId, 4);

        mockMvc.perform(post("/product/{id}/restock", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.lifecycle").value(3));
    }

    @Test
    void restockInvalidState() throws Exception {
        long farmerUserId = createFarmer("13800000029");
        long spuId = createSpu(createCategory("芒果"), "海南红心芒果");
        long productId = createProduct(farmerUserId, spuId, "海南红心芒果 5斤装");

        mockMvc.perform(post("/product/{id}/restock", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30019));
    }

    private long createAndApprove(long farmerUserId, long spuId, String title) throws Exception {
        long productId = createProduct(farmerUserId, spuId, title);
        approve(productId);
        return productId;
    }

    private void approve(long productId) throws Exception {
        mockMvc.perform(post("/product/{id}/audit", productId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(1, null))))
                .andExpect(status().isOk());
    }

    private ProductUpdateDTO updateDto(long spuId, String title, String price, int stock, int version) {
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setSpuId(spuId);
        dto.setTitle(title);
        dto.setPrice(new BigDecimal(price));
        dto.setStock(stock);
        dto.setVersion(version);
        return dto;
    }

    private void changeLifecycle(long productId, long farmerUserId, int target) throws Exception {
        ProductLifecycleUpdateDTO dto = new ProductLifecycleUpdateDTO();
        dto.setLifecycle(target);
        mockMvc.perform(post("/product/{id}/lifecycle", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycle").value(target));
    }

    private void assertLifecycleIllegal(long productId, long farmerUserId, int target) throws Exception {
        ProductLifecycleUpdateDTO dto = new ProductLifecycleUpdateDTO();
        dto.setLifecycle(target);
        mockMvc.perform(post("/product/{id}/lifecycle", productId)
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30019));
    }

    private String adminToken() {
        return jwtUtils.generateAccessToken(1L, 1);
    }

    private String userToken() {
        return jwtUtils.generateAccessToken(2L, 0);
    }

    private String farmerToken(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }

    private long createFarmer(String phone) {
        User user = new User();
        user.setUsername("farmer_" + phone);
        user.setPasswordHash("x");
        user.setPhone(phone);
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted-id-card");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return user.getId();
    }

    private long createCategory(String name) throws Exception {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName(name);
        dto.setSortOrder(0);
        MvcResult result = mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createSpu(long categoryId, String name) throws Exception {
        SpuCreateDTO dto = new SpuCreateDTO();
        dto.setCategoryId(categoryId);
        dto.setName(name);
        dto.setStatus(1);
        MvcResult result = mockMvc.perform(post("/spu")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createProduct(long farmerUserId, long spuId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreate(spuId, title, "49.90", 100))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createProductWithAttributes(long farmerUserId, long spuId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/product")
                        .header("Authorization", "Bearer " + farmerToken(farmerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productCreateWithAttributes(spuId, title))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private ProductCreateDTO productCreate(long spuId, String title, String price, int stock) {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setSpuId(spuId);
        dto.setTitle(title);
        dto.setPrice(new BigDecimal(price));
        dto.setStock(stock);
        return dto;
    }

    private ProductCreateDTO productCreateWithAttributes(long spuId, String title) {
        ProductCreateDTO dto = productCreate(spuId, title, "49.90", 100);

        ProductAttributeDTO attr1 = new ProductAttributeDTO();
        attr1.setAttrName("规格");
        attr1.setAttrValue("5斤装");
        attr1.setExtraPrice(BigDecimal.ZERO);

        ProductAttributeDTO attr2 = new ProductAttributeDTO();
        attr2.setAttrName("规格");
        attr2.setAttrValue("10斤装");
        attr2.setExtraPrice(new BigDecimal("30.00"));

        ProductImageDTO image = new ProductImageDTO();
        image.setImageUrl("http://img/1.png");
        image.setSortOrder(0);

        dto.setAttributes(List.of(attr1, attr2));
        dto.setImages(List.of(image));
        return dto;
    }

    private ProductAuditDTO audit(int auditStatus, String reason) {
        ProductAuditDTO dto = new ProductAuditDTO();
        dto.setAuditStatus(auditStatus);
        dto.setAuditReason(reason);
        return dto;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
