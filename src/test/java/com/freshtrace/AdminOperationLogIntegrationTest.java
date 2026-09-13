package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.product.dto.CategoryCreateDTO;
import com.freshtrace.product.dto.CategoryUpdateDTO;
import com.freshtrace.report.dto.ReportHandleDTO;
import com.freshtrace.report.dto.ReportSubmitDTO;
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

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 操作日志切面 + 查询集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AdminOperationLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void operationLogRecordedAndQueryable() throws Exception {
        long adminId = createUser(1);
        long userId = createUser(0);
        String adminToken = token(adminId, 1);

        // 1) 处理举报 → 产生 REPORT 操作日志
        mockMvc.perform(post("/report")
                        .header("Authorization", "Bearer " + token(userId, 0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(report(2, 1001L, "商品与描述不符"))))
                .andExpect(status().isOk());
        long reportId = queryId("SELECT id FROM t_report");
        mockMvc.perform(post("/admin/report/{id}/handle", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(handle(1, "举报属实"))))
                .andExpect(status().isOk());

        // 2) 品类增改删 → 产生 3 条 CATEGORY 操作日志
        String createBody = json(category("芒果", 1));
        String created = mockMvc.perform(post("/category")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long categoryId = objectMapper.readTree(created).path("data").path("id").asLong();
        mockMvc.perform(put("/category/{id}", categoryId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(categoryUpdate("芒果2", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/category/{id}", categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 全部 4 条
        mockMvc.perform(get("/admin/operation-log/list")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[0].operatorName").isNotEmpty());

        // 按对象类型过滤
        mockMvc.perform(get("/admin/operation-log/list")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("targetType", "CATEGORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        // 按动作过滤
        mockMvc.perform(get("/admin/operation-log/list")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "处理举报"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // 按操作人 + 时间段过滤
        mockMvc.perform(get("/admin/operation-log/list")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("operatorId", String.valueOf(adminId))
                        .param("startTime", "2000-01-01T00:00:00")
                        .param("endTime", "2100-01-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));

        // 非管理员无权查询
        mockMvc.perform(get("/admin/operation-log/list")
                        .header("Authorization", "Bearer " + token(userId, 0)))
                .andExpect(status().isForbidden());
    }

    private ReportSubmitDTO report(int targetType, long targetId, String reason) {
        ReportSubmitDTO dto = new ReportSubmitDTO();
        dto.setTargetType(targetType);
        dto.setTargetId(targetId);
        dto.setReason(reason);
        return dto;
    }

    private ReportHandleDTO handle(int status, String reason) {
        ReportHandleDTO dto = new ReportHandleDTO();
        dto.setStatus(status);
        dto.setHandleReason(reason);
        return dto;
    }

    private CategoryCreateDTO category(String name, int sortOrder) {
        CategoryCreateDTO dto = new CategoryCreateDTO();
        dto.setName(name);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    private CategoryUpdateDTO categoryUpdate(String name, int sortOrder) {
        CategoryUpdateDTO dto = new CategoryUpdateDTO();
        dto.setName(name);
        dto.setSortOrder(sortOrder);
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
