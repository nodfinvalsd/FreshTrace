package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.security.JwtBlacklistService;
import com.freshtrace.user.dto.AddressDTO;
import com.freshtrace.user.dto.LoginDTO;
import com.freshtrace.user.dto.LogoutDTO;
import com.freshtrace.user.dto.RefreshDTO;
import com.freshtrace.user.dto.RegisterDTO;
import com.freshtrace.user.dto.UpdateProfileDTO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

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
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void registerSuccess() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register("alice", "13800000001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void registerDuplicateUsername() throws Exception {
        registerByHttp("alice", "13800000001");
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register("alice", "13800000002"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    void registerDuplicatePhone() throws Exception {
        registerByHttp("alice", "13800000001");
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register("bob", "13800000001"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30002));
    }

    @Test
    void passwordIsBcryptHashed() throws Exception {
        registerByHttp("alice", "13800000001");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "alice"));
        assertThat(user).isNotNull();
        assertThat(user.getPasswordHash()).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches("123456", user.getPasswordHash())).isTrue();
    }

    @Test
    void loginReturnsTwoTokens() throws Exception {
        registerByHttp("alice", "13800000001");
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login("alice", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();
        assertThat(accessToken).isNotEqualTo(refreshToken);

        Claims accessClaims = jwtUtils.parseToken(accessToken);
        Claims refreshClaims = jwtUtils.parseToken(refreshToken);
        assertThat(accessClaims.getId()).isNotEqualTo(refreshClaims.getId());
        assertThat(accessClaims.get("tokenType", String.class)).isEqualTo("access");
        assertThat(refreshClaims.get("tokenType", String.class)).isEqualTo("refresh");
    }

    @Test
    void loginWrongPassword() throws Exception {
        registerByHttp("alice", "13800000001");
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login("alice", "wrong-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30003));
    }

    @Test
    void getMe() throws Exception {
        String token = registerAndLogin("alice", "13800000001");
        mockMvc.perform(get("/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void refreshTokenCannotAccessMe() throws Exception {
        Tokens tokens = registerAndLoginTokens("alice", "13800000001");
        mockMvc.perform(get("/user/me").header("Authorization", "Bearer " + tokens.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSuccess() throws Exception {
        Tokens tokens = registerAndLoginTokens("alice", "13800000001");
        MvcResult result = mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refresh(tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String newAccessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        mockMvc.perform(get("/user/me").header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshDoesNotNeedAccessToken() throws Exception {
        Tokens tokens = registerAndLoginTokens("alice", "13800000001");
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refresh(tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void refreshWithExpiredTokenFails() throws Exception {
        long userId = registerReturningUserId("alice", "13800000001");
        String expiredRefresh = tokenWithTtl(userId, null, "refresh", -1000L);
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refresh(expiredRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithBlacklistedTokenFails() throws Exception {
        Tokens tokens = registerAndLoginTokens("alice", "13800000001");
        String refreshToken = tokens.refreshToken();
        jwtBlacklistService.blacklistRefresh(jwtUtils.getJti(refreshToken), 1000L);
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refresh(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesBothTokens() throws Exception {
        Tokens tokens = registerAndLoginTokens("alice", "13800000001");
        mockMvc.perform(post("/user/logout")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(logout(tokens.refreshToken()))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/user/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(refresh(tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addressCrud() throws Exception {
        String token = registerAndLogin("alice", "13800000001");

        MvcResult created = mockMvc.perform(post("/user/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("张三", "13811111111", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        long addressId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/user/address/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(put("/user/address/{id}", addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("李四", "13822222222", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("李四"));

        mockMvc.perform(delete("/user/address/{id}", addressId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/user/address/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void setDefaultAddress() throws Exception {
        String token = registerAndLogin("alice", "13800000001");
        long addr1 = createAddress(token, "张三", "13811111111");
        long addr2 = createAddress(token, "李四", "13822222222");

        mockMvc.perform(put("/user/address/{id}/default", addr2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(1));

        mockMvc.perform(get("/user/address/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/user/address/{id}", addr1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(0));
    }

    @Test
    void cannotModifyOtherUserAddress() throws Exception {
        String tokenA = registerAndLogin("alice", "13800000001");
        String tokenB = registerAndLogin("bob", "13800000002");
        long addressOfA = createAddress(tokenA, "张三", "13811111111");

        mockMvc.perform(put("/user/address/{id}", addressOfA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address("黑客", "13833333333", 0))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addressLogicalDelete() throws Exception {
        String token = registerAndLogin("alice", "13800000001");
        long addressId = createAddress(token, "张三", "13811111111");
        mockMvc.perform(delete("/user/address/{id}", addressId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/user/address/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_address WHERE id = ?", Integer.class, addressId);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void updateProfile() throws Exception {
        String token = registerAndLogin("alice", "13800000001");
        mockMvc.perform(put("/user/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateProfile("新昵称", "http://img/a.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        mockMvc.perform(get("/user/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatarUrl").value("http://img/a.png"));
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    private long createAddress(String token, String name, String phone) throws Exception {
        MvcResult created = mockMvc.perform(post("/user/address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address(name, phone, 0))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void registerByHttp(String username, String phone) throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register(username, phone))))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String username, String phone) throws Exception {
        return registerAndLoginTokens(username, phone).accessToken();
    }

    private Tokens registerAndLoginTokens(String username, String phone) throws Exception {
        registerByHttp(username, phone);
        MvcResult result = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(login(username, "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private long registerReturningUserId(String username, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register(username, phone))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String tokenWithTtl(Long userId, Integer role, String tokenType, long ttlMillis) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("tokenType", tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMillis));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
    }

    private UpdateProfileDTO updateProfile(String nickname, String avatarUrl) {
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname(nickname);
        dto.setAvatarUrl(avatarUrl);
        return dto;
    }

    private RegisterDTO register(String username, String phone) {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername(username);
        dto.setPassword("123456");
        dto.setPhone(phone);
        return dto;
    }

    private LoginDTO login(String account, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setAccount(account);
        dto.setPassword(password);
        return dto;
    }

    private RefreshDTO refresh(String refreshToken) {
        RefreshDTO dto = new RefreshDTO();
        dto.setRefreshToken(refreshToken);
        return dto;
    }

    private LogoutDTO logout(String refreshToken) {
        LogoutDTO dto = new LogoutDTO();
        dto.setRefreshToken(refreshToken);
        return dto;
    }

    private AddressDTO address(String name, String phone, int isDefault) {
        AddressDTO dto = new AddressDTO();
        dto.setReceiverName(name);
        dto.setReceiverPhone(phone);
        dto.setProvince("广东省");
        dto.setCity("深圳市");
        dto.setDistrict("南山区");
        dto.setDetail("科技园1号");
        dto.setIsDefault(isDefault);
        return dto;
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
