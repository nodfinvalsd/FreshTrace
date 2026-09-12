package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.entity.ChatMessage;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.vo.ConversationVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 已读与未读总数集成测试（Phase 8 Day 4）：
 * 标记已读 → 会话未读清零 + 消息置已读 → 全局未读数归零；非成员越权被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ImReadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void markReadClearsUnreadAndMarksMessages() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        // 果农发两条 → 买家未读 2
        send(farmer.userId(), conversationId, "第一条");
        send(farmer.userId(), conversationId, "第二条");
        assertThat(conversationMapper.selectById(conversationId).getUnreadUser()).isEqualTo(2);
        assertThat(unreadCount(buyerId)).isEqualTo(2);

        // 买家标记已读
        mockMvc.perform(put("/im/conversations/" + conversationId + "/read")
                        .header("Authorization", "Bearer " + token(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(conversationMapper.selectById(conversationId).getUnreadUser()).isZero();
        assertThat(unreadCount(buyerId)).isZero();

        List<ChatMessage> messages = chatMessageMapper.selectList(null);
        assertThat(messages).hasSize(2).allMatch(m -> m.getIsRead() == 1);
    }

    @Test
    void markReadRejectedForNonMember() throws Exception {
        long buyerId = createBuyer();
        long strangerId = createBuyer();
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());
        send(farmer.userId(), conversationId, "hello");

        mockMvc.perform(put("/im/conversations/" + conversationId + "/read")
                        .header("Authorization", "Bearer " + token(strangerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30055));
    }

    @Test
    void farmerUnreadCountTrackedSeparately() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        // 买家发两条 → 果农未读 2；买家自己未读 0
        send(buyerId, conversationId, "在吗");
        send(buyerId, conversationId, "有货吗");
        assertThat(unreadCount(farmer.userId())).isEqualTo(2);
        assertThat(unreadCount(buyerId)).isZero();
    }

    private void send(long senderId, Long conversationId, String content) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setType("CHAT");
        dto.setConversationId(conversationId);
        dto.setContent(content);
        dto.setClientMsgId("c-" + seq.incrementAndGet());
        chatMessageService.send(senderId, dto);
    }

    private long unreadCount(long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/im/unread-count")
                        .header("Authorization", "Bearer " + token(userId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").asLong();
    }

    private Long createConversation(long buyerId, long farmerId) {
        ConversationCreateDTO dto = new ConversationCreateDTO();
        dto.setFarmerId(farmerId);
        ConversationVO vo = conversationService.create(buyerId, dto);
        return vo.getId();
    }

    private long createBuyer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("buyer_" + n);
        user.setPasswordHash("x");
        user.setNickname("买家" + n);
        user.setPhone("138" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
    }

    private FarmerRef createFarmer() {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("farmer_" + n);
        user.setPasswordHash("x");
        user.setNickname("果农" + n);
        user.setPhone("137" + String.format("%08d", n % 100000000));
        user.setRole(0);
        user.setStatus(1);
        userMapper.insert(user);

        Farmer farmer = new Farmer();
        farmer.setUserId(user.getId());
        farmer.setRealName("张三");
        farmer.setIdCard("encrypted");
        farmer.setOrchardName("测试果园");
        farmer.setOrchardProvince("广东省");
        farmer.setOrchardCity("深圳市");
        farmer.setOrchardDistrict("南山区");
        farmer.setOrchardAddress("某村1号");
        farmer.setAuditStatus(1);
        farmerMapper.insert(farmer);
        return new FarmerRef(farmer.getId(), user.getId());
    }

    private String token(long userId) {
        return jwtUtils.generateAccessToken(userId, 0);
    }

    private record FarmerRef(Long farmerId, Long userId) {
    }
}
