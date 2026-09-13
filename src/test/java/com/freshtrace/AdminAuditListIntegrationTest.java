package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 管理端审核列表/详情集成测试（Day 1 接口回归）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AdminAuditListIntegrationTest {

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

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void farmerAuditListAndDetail() throws Exception {
        long userId = createUser(0);
        long adminId = createUser(1);

        // 用户提交果农认证（身份证号 AES 加密存储）
        mockMvc.perform(post("/farmer/apply")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(apply("李四", "123456789012345678"))))
                .andExpect(status().isOk());

        long farmerId = queryId("SELECT id FROM t_farmer");

        // 待审核列表
        mockMvc.perform(get("/admin/farmer/list")
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .param("auditStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].realName").value("李四"))
                // 列表脱敏：不返回身份证号
                .andExpect(jsonPath("$.data.records[0].idCard").doesNotExist());

        // 详情：解密身份证号供审核
        mockMvc.perform(get("/admin/farmer/{id}", farmerId)
                        .header("Authorization", "Bearer " + token(adminId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("李四"))
                .andExpect(jsonPath("$.data.idCard").value("123456789012345678"))
                .andExpect(jsonPath("$.data.orchardName").value("测试果园"));

        // 审核通过后，待审核列表为空、已通过列表 1 条
        FarmerAuditDTO audit = new FarmerAuditDTO();
        audit.setFarmerId(farmerId);
        audit.setAuditStatus(1);
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/farmer/list")
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .param("auditStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void productAuditList() throws Exception {
        long adminId = createUser(1);
        long farmerId = createFarmerRow("王五", "测试果园");
        long productId = createProductRow(farmerId);

        mockMvc.perform(get("/admin/product/audit-list")
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .param("auditStatus", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(productId))
                .andExpect(jsonPath("$.data.records[0].farmerName").value("王五"))
                .andExpect(jsonPath("$.data.records[0].orchardName").value("测试果园"));

        mockMvc.perform(get("/admin/product/{id}", productId)
                        .header("Authorization", "Bearer " + token(adminId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("海南芒果"))
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    private FarmerApplyDTO apply(String realName, String idCard) {
        FarmerApplyDTO dto = new FarmerApplyDTO();
        dto.setRealName(realName);
        dto.setIdCard(idCard);
        dto.setOrchardName("测试果园");
        dto.setOrchardProvince("广东省");
        dto.setOrchardCity("深圳市");
        dto.setOrchardDistrict("南山区");
        dto.setOrchardAddress("某村1号");
        return dto;
    }

    private long createUser(int role) {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("u_" + n);
        user.setPasswordHash("x");
        user.setPhone("13" + String.format("%09d", n % 1000000000));
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private long createFarmerRow(String realName, String orchardName) {
        long userId = createUser(0);
        Farmer farmer = new Farmer();
        farmer.setUserId(userId);
        farmer.setRealName(realName);
        farmer.setIdCard("encrypted");
        farmer.setOrchardName(orchardName);
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return farmer.getId();
    }

    private long createProductRow(long farmerId) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(farmerId);
        product.setTitle("海南芒果");
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(100);
        product.setUnit("斤");
        product.setLifecycle(0);
        product.setAuditStatus(0);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long queryId(String sql) {
        Long id = jdbcTemplate.queryForObject(sql, Long.class);
        return id == null ? 0 : id;
    }

    private String token(long userId, int role) {
        return jwtUtils.generateAccessToken(userId, role);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
