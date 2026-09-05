package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void protectedApiWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/auth-test/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleReturns403() throws Exception {
        String token = jwtUtils.generateAccessToken(100L, 0);
        mockMvc.perform(get("/auth-test/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctRoleReturns200() throws Exception {
        String token = jwtUtils.generateAccessToken(100L, 1);
        mockMvc.perform(get("/auth-test/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
