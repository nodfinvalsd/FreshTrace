package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.common.utils.AesUtils;
import com.freshtrace.farmer.dto.FarmerApplyDTO;
import com.freshtrace.farmer.dto.FarmerAuditDTO;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.RegisterDTO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class FarmerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AesUtils aesUtils;

    @Test
    void applySuccess() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        mockMvc.perform(post("/farmer/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(apply("张三", "110101199001011234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void applyDuplicate() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        mockMvc.perform(post("/farmer/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(apply("张三", "110101199001011234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30007));
    }

    @Test
    void idCardEncrypted() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));

        Farmer farmer = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getRealName, "张三"));
        assertThat(farmer).isNotNull();
        assertThat(farmer.getIdCard()).isNotEqualTo("110101199001011234");
        assertThat(aesUtils.decrypt(farmer.getIdCard())).isEqualTo("110101199001011234");
    }

    @Test
    void getStatus() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        mockMvc.perform(get("/farmer/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.auditStatus").value(0));
    }

    @Test
    void auditApprove() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/farmer/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(1));
    }

    @Test
    void auditReject() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 2, "证件不清晰"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/farmer/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(2))
                .andExpect(jsonPath("$.data.auditReason").value("证件不清晰"));
    }

    @Test
    void normalUserCannotAudit() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedFarmerAccessFails() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        mockMvc.perform(get("/farmer/privilege").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedFarmerAccessSucceeds() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/farmer/privilege").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void pendingFarmerAccessFails() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        mockMvc.perform(get("/farmer/privilege").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectedFarmerAccessFails() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 2, "证件不清晰"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/farmer/privilege").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditAlreadyApprovedFails() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 2, "复审驳回"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30010));
    }

    @Test
    void auditAlreadyRejectedFails() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        String adminToken = createAdmin();
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 2, "证件不清晰"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30010));
    }

    @Test
    void auditPendingToApproved() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        long adminId = createAdminId();
        String adminToken = jwtUtils.generateToken(adminId, 1);

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 1, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Farmer farmer = farmerMapper.selectById(farmerId);
        assertThat(farmer.getAuditStatus()).isEqualTo(1);
        assertThat(farmer.getAuditedBy()).isEqualTo(adminId);
        assertThat(farmer.getAuditedAt()).isNotNull();
    }

    @Test
    void auditPendingToRejected() throws Exception {
        String token = registerAndLogin("farmer1", "13800000001");
        applyByHttp(token, apply("张三", "110101199001011234"));
        long farmerId = farmerIdByRealName("张三");
        long adminId = createAdminId();
        String adminToken = jwtUtils.generateToken(adminId, 1);

        mockMvc.perform(post("/admin/farmer/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(audit(farmerId, 2, "证件不清晰"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Farmer farmer = farmerMapper.selectById(farmerId);
        assertThat(farmer.getAuditStatus()).isEqualTo(2);
        assertThat(farmer.getAuditReason()).isEqualTo("证件不清晰");
        assertThat(farmer.getAuditedBy()).isEqualTo(adminId);
        assertThat(farmer.getAuditedAt()).isNotNull();
    }

    private long farmerIdByRealName(String realName) {
        Farmer farmer = farmerMapper.selectOne(new LambdaQueryWrapper<Farmer>()
                .eq(Farmer::getRealName, realName));
        return farmer.getId();
    }

    private String createAdmin() {
        return jwtUtils.generateToken(createAdminId(), 1);
    }

    private long createAdminId() {
        User admin = new User();
        admin.setUsername("admin_test");
        admin.setPasswordHash("x");
        admin.setPhone("13900000000");
        admin.setRole(1);
        admin.setStatus(1);
        userMapper.insert(admin);
        return admin.getId();
    }

    private void applyByHttp(String token, FarmerApplyDTO dto) throws Exception {
        mockMvc.perform(post("/farmer/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dto)))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String username, String phone) throws Exception {
        RegisterDTO register = new RegisterDTO();
        register.setUsername(username);
        register.setPassword("123456");
        register.setPhone(phone);
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register)))
                .andExpect(status().isOk());

        LoginDTO login = new LoginDTO();
        login.setAccount(username);
        login.setPassword("123456");
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
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

    private FarmerAuditDTO audit(long farmerId, int auditStatus, String reason) {
        FarmerAuditDTO dto = new FarmerAuditDTO();
        dto.setFarmerId(farmerId);
        dto.setAuditStatus(auditStatus);
        dto.setAuditReason(reason);
        return dto;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
