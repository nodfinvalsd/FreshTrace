package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.report.dto.ReportHandleDTO;
import com.freshtrace.report.dto.ReportSubmitDTO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 举报处理 + 运营仪表板集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AdminReportDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @BeforeEach
    void clearDashboardCache() {
        stringRedisTemplate.delete("admin:dashboard:overview");
    }

    @Test
    void reportSubmitAndAdminHandleFlow() throws Exception {
        long userId = createUser(0);
        long adminId = createUser(1);

        // 用户提交举报（带凭证图）
        mockMvc.perform(post("/report")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(report(2, 1001L, "商品与描述不符", List.of("http://img/a.jpg")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 管理端列表：待处理 1 条
        mockMvc.perform(get("/admin/report/list")
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].targetTypeDesc").value("商品品质"));

        long reportId = queryId();

        // 详情：含凭证图片
        mockMvc.perform(get("/admin/report/{id}", reportId)
                        .header("Authorization", "Bearer " + token(adminId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusDesc").value("待处理"))
                .andExpect(jsonPath("$.data.evidenceImages[0]").value("http://img/a.jpg"));

        // 处理：有效
        mockMvc.perform(post("/admin/report/{id}/handle", reportId)
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle(1, "举报属实"))))
                .andExpect(status().isOk());

        // 重复处理失败
        mockMvc.perform(post("/admin/report/{id}/handle", reportId)
                        .header("Authorization", "Bearer " + token(adminId, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle(2, "重复处理"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30068));

        // 非管理员无权处理
        mockMvc.perform(get("/admin/report/list")
                        .header("Authorization", "Bearer " + token(userId, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReportWithInvalidTargetTypeFails() throws Exception {
        long userId = createUser(0);
        mockMvc.perform(post("/report")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(report(9, 1L, "非法类型", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30070));
    }

    @Test
    void dashboardOverviewAggregatesPendingAndNewUsers() throws Exception {
        long userId = createUser(0);
        long adminId = createUser(1);
        mockMvc.perform(post("/report")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(report(3, 2002L, "果农违规", null))))
                .andExpect(status().isOk());

        Integer direct = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE deleted = 0 AND create_time >= ? AND create_time < ?",
                Integer.class, java.time.LocalDate.now().atStartOfDay(),
                java.time.LocalDate.now().plusDays(1).atStartOfDay());
        org.assertj.core.api.Assertions.assertThat(direct).isEqualTo(2);

        mockMvc.perform(get("/admin/dashboard/overview")
                        .header("Authorization", "Bearer " + token(adminId, 1)))
                .andExpect(status().isOk())
                // 本次测试新建 2 个用户
                .andExpect(jsonPath("$.data.today.newUsers").value(2))
                // 待处理举报 1 条
                .andExpect(jsonPath("$.data.pending.reports").value(1))
                .andExpect(jsonPath("$.data.today.orderCount").value(0));
    }

    private ReportSubmitDTO report(int targetType, long targetId, String reason, List<String> images) {
        ReportSubmitDTO dto = new ReportSubmitDTO();
        dto.setTargetType(targetType);
        dto.setTargetId(targetId);
        dto.setReason(reason);
        dto.setEvidenceImages(images);
        return dto;
    }

    private ReportHandleDTO handle(int status, String reason) {
        ReportHandleDTO dto = new ReportHandleDTO();
        dto.setStatus(status);
        dto.setHandleReason(reason);
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

    private long queryId() {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM t_report", Long.class);
        return id == null ? 0 : id;
    }

    private String token(long userId, int role) {
        return jwtUtils.generateAccessToken(userId, role);
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
