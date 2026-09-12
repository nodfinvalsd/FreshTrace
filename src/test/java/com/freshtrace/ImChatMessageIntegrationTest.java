package com.freshtrace;

import com.freshtrace.common.BizException;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.entity.Conversation;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.im.vo.ChatHistoryVO;
import com.freshtrace.im.vo.ChatMessageVO;
import com.freshtrace.im.vo.ConversationVO;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 聊天消息服务集成测试（Phase 8 Day 3）：
 * 落库与未读同步、身份推导、历史游标分页、越权与内容校验。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ImChatMessageIntegrationTest {

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
    void sendPersistsMessageAndSyncsConversation() {
        long buyerId = createBuyer("买家");
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        ChatMessageVO fromBuyer = chatMessageService.send(buyerId, frame(conversationId, "芒果怎么卖？"));
        assertThat(fromBuyer.getMessageId()).isPositive();
        assertThat(fromBuyer.getSenderRole()).isZero();

        Conversation afterBuyer = conversationMapper.selectById(conversationId);
        assertThat(afterBuyer.getLastMessage()).isEqualTo("芒果怎么卖？");
        assertThat(afterBuyer.getUnreadUser()).isZero();
        assertThat(afterBuyer.getUnreadFarmer()).isEqualTo(1);

        ChatMessageVO fromFarmer = chatMessageService.send(farmer.userId(), frame(conversationId, "20一斤"));
        assertThat(fromFarmer.getSenderRole()).isEqualTo(1);

        Conversation afterFarmer = conversationMapper.selectById(conversationId);
        assertThat(afterFarmer.getLastMessage()).isEqualTo("20一斤");
        assertThat(afterFarmer.getUnreadUser()).isEqualTo(1);
        assertThat(afterFarmer.getUnreadFarmer()).isEqualTo(1);
        assertThat(chatMessageMapper.selectCount(null)).isEqualTo(2);
    }

    @Test
    void historyUsesCursorPagination() {
        long buyerId = createBuyer("买家");
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());
        for (int i = 1; i <= 5; i++) {
            chatMessageService.send(buyerId, frame(conversationId, "消息" + i));
        }

        ChatHistoryVO firstPage = chatMessageService.history(buyerId, conversationId, null, 2);
        assertThat(firstPage.getRecords()).hasSize(2);
        assertThat(firstPage.isHasMore()).isTrue();
        assertThat(firstPage.getRecords().get(0).getContent()).isEqualTo("消息5");
        Long cursor = firstPage.getNextCursor();

        ChatHistoryVO secondPage = chatMessageService.history(buyerId, conversationId, cursor, 2);
        assertThat(secondPage.getRecords()).extracting(ChatMessageVO::getContent)
                .containsExactly("消息3", "消息2");

        ChatHistoryVO thirdPage = chatMessageService.history(buyerId, conversationId, secondPage.getNextCursor(), 2);
        assertThat(thirdPage.getRecords()).extracting(ChatMessageVO::getContent).containsExactly("消息1");
        assertThat(thirdPage.isHasMore()).isFalse();
    }

    @Test
    void rejectsNonMember() {
        long buyerId = createBuyer("买家");
        long strangerId = createBuyer("路人");
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        assertThatThrownBy(() -> chatMessageService.send(strangerId, frame(conversationId, "偷看")))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(30055);
        assertThatThrownBy(() -> chatMessageService.history(strangerId, conversationId, null, 10))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(30055);
    }

    @Test
    void rejectsBlankAndTooLongContent() {
        long buyerId = createBuyer("买家");
        FarmerRef farmer = createFarmer();
        Long conversationId = createConversation(buyerId, farmer.farmerId());

        assertThatThrownBy(() -> chatMessageService.send(buyerId, frame(conversationId, "   ")))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10000);
        assertThatThrownBy(() -> chatMessageService.send(buyerId, frame(conversationId, "x".repeat(1001))))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(30056);
    }

    private ChatMessageDTO frame(Long conversationId, String content) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setType("CHAT");
        dto.setConversationId(conversationId);
        dto.setContent(content);
        dto.setClientMsgId("c-" + seq.incrementAndGet());
        return dto;
    }

    private Long createConversation(long buyerId, long farmerId) {
        ConversationCreateDTO dto = new ConversationCreateDTO();
        dto.setFarmerId(farmerId);
        ConversationVO vo = conversationService.create(buyerId, dto);
        return vo.getId();
    }

    private long createBuyer(String nickname) {
        long n = seq.incrementAndGet();
        User user = new User();
        user.setUsername("buyer_" + n);
        user.setPasswordHash("x");
        user.setNickname(nickname + n);
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
