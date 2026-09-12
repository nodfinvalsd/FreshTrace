package com.freshtrace;

import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.im.dto.ChatMessageDTO;
import com.freshtrace.im.dto.ConversationCreateDTO;
import com.freshtrace.im.mapper.ChatMessageMapper;
import com.freshtrace.im.mapper.ConversationMapper;
import com.freshtrace.im.service.ChatMessageService;
import com.freshtrace.im.service.ConversationService;
import com.freshtrace.user.entity.User;
import com.freshtrace.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IM 并发测试（Phase 8 Day 4）：
 * 1) 同一对买卖双方并发创建会话只落一条（UNIQUE + 冲突回查）；
 * 2) 同一会话并发发送消息，未读计数原子累加不丢失。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ImConcurrencyTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FarmerMapper farmerMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void concurrentConversationCreateKeepsSingleRow() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                ConversationCreateDTO dto = new ConversationCreateDTO();
                dto.setFarmerId(farmer.farmerId());
                return conversationService.create(buyerId, dto).getId();
            }));
        }
        start.countDown();

        Set<Long> ids = new HashSet<>();
        for (Future<Long> future : futures) {
            ids.add(future.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(conversationMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void concurrentSendAccumulatesUnreadExactly() throws Exception {
        long buyerId = createBuyer();
        FarmerRef farmer = createFarmer();
        ConversationCreateDTO createDTO = new ConversationCreateDTO();
        createDTO.setFarmerId(farmer.farmerId());
        Long conversationId = conversationService.create(buyerId, createDTO).getId();

        int messages = 20;
        ExecutorService pool = Executors.newFixedThreadPool(messages);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < messages; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                ChatMessageDTO dto = new ChatMessageDTO();
                dto.setType("CHAT");
                dto.setConversationId(conversationId);
                dto.setContent("并发消息" + index);
                dto.setClientMsgId("m-" + index);
                return chatMessageService.send(buyerId, dto).getMessageId();
            }));
        }
        start.countDown();
        for (Future<Long> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(chatMessageMapper.selectCount(null)).isEqualTo(messages);
        assertThat(conversationMapper.selectById(conversationId).getUnreadFarmer()).isEqualTo(messages);
        assertThat(conversationMapper.selectById(conversationId).getUnreadUser()).isZero();
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
