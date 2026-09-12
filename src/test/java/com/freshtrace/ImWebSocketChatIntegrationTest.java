package com.freshtrace;

import com.freshtrace.common.JwtUtils;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.vo.ConversationVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 聊天端到端测试（Phase 8 Day 3）：
 * 双方在线时，买家发 CHAT → 果农实时收到 CHAT，买家收到 ACK，消息落库并累加未读。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ImWebSocketChatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void buyerMessageIsPushedToFarmerAndAckedToBuyer() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        BlockingQueue<String> buyerInbox = new LinkedBlockingQueue<>();
        BlockingQueue<String> farmerInbox = new LinkedBlockingQueue<>();
        WebSocketSession buyerSession = connect(buyerId, buyerInbox);
        WebSocketSession farmerSession = connect(farmer.userId(), farmerInbox);
        awaitReady(buyerSession, buyerInbox);
        awaitReady(farmerSession, farmerInbox);

        buyerSession.sendMessage(new TextMessage("{\"type\":\"CHAT\",\"conversationId\":" + conversationId
                + ",\"content\":\"你好\",\"clientMsgId\":\"m1\"}"));

        String ack = buyerInbox.poll(5, TimeUnit.SECONDS);
        assertThat(ack).contains("\"type\":\"ACK\"").contains("m1");

        String chat = farmerInbox.poll(5, TimeUnit.SECONDS);
        assertThat(chat).contains("\"type\":\"CHAT\"").contains("你好");

        assertThat(chatMessageMapper.selectCount(null)).isEqualTo(1);
        Conversation conversation = conversationMapper.selectById(conversationId);
        assertThat(conversation.getLastMessage()).isEqualTo("你好");
        assertThat(conversation.getUnreadFarmer()).isEqualTo(1);

        buyerSession.close();
        farmerSession.close();
    }

    private WebSocketSession connect(long userId, BlockingQueue<String> inbox) throws Exception {
        return new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                inbox.add(message.getPayload());
            }
        }, wsUrl(userId)).get(5, TimeUnit.SECONDS);
    }

    /**
     * 通过 PING/PONG 确认服务端已完成 session 注册，消除建连与注册之间的竞态。
     */
    private void awaitReady(WebSocketSession session, BlockingQueue<String> inbox) throws Exception {
        session.sendMessage(new TextMessage("{\"type\":\"PING\"}"));
        String pong = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(pong).contains("PONG");
    }

    private String wsUrl(long userId) {
        return "ws://localhost:" + port + "/api/ws/chat?token=" + jwtUtils.generateAccessToken(userId, 0);
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

    private record FarmerRef(Long farmerId, Long userId) {
    }
}
