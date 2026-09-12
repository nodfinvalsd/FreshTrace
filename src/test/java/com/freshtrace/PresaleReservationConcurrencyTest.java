package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.presale.dto.ReserveDTO;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.service.PresaleReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约并发测试（Phase 7 Day 2）：
 * 1. 不同用户并发预约且名额有限 → 成功数严格等于 max_reservations，DB 计数与 Redis 计数一致；
 * 2. 同一用户并发预约 → UNIQUE(presale_id,user_id) 兜底，仅一行、计数为 1。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PresaleReservationConcurrencyTest {

    @Autowired
    private PresaleReservationService presaleReservationService;

    @Autowired
    private PresaleMapper presaleMapper;

    @Autowired
    private PresaleReservationMapper presaleReservationMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void concurrentReservationsRespectMaxLimit() throws Exception {
        int max = 5;
        long presaleId = createPresale(max);
        int users = 12;

        runConcurrently(users, index -> {
            try {
                presaleReservationService.reserve(810000L + index, presaleId, reserveDTO());
            } catch (BizException ignored) {
                // 名额已满属预期
            }
        });

        assertThat(reservationRows(presaleId)).isEqualTo(max);
        assertThat(presaleMapper.selectById(presaleId).getReservationCount()).isEqualTo(max);
        assertThat(redisCount(presaleId)).isEqualTo(String.valueOf(max));
    }

    @Test
    void concurrentDuplicateReservationBySameUserCountsOnce() throws Exception {
        long presaleId = createPresale(0);
        long userId = 700999L;
        int threads = 10;

        runConcurrently(threads, index -> {
            try {
                presaleReservationService.reserve(userId, presaleId, reserveDTO());
            } catch (BizException ignored) {
                // 重复预约属预期
            }
        });

        assertThat(reservationRows(presaleId)).isEqualTo(1);
        assertThat(presaleMapper.selectById(presaleId).getReservationCount()).isEqualTo(1);
        assertThat(redisCount(presaleId)).isEqualTo("1");
    }

    private ReserveDTO reserveDTO() {
        ReserveDTO dto = new ReserveDTO();
        dto.setQuantity(1);
        return dto;
    }

    private long reservationRows(long presaleId) {
        return presaleReservationMapper.selectCount(new LambdaQueryWrapper<PresaleReservation>()
                .eq(PresaleReservation::getPresaleId, presaleId));
    }

    private String redisCount(long presaleId) {
        return stringRedisTemplate.opsForValue().get(CacheKeys.presaleCount(presaleId));
    }

    private long createPresale(int maxReservations) {
        Presale presale = new Presale();
        presale.setProductId(seq.incrementAndGet());
        presale.setFarmerId(1L);
        presale.setPresaleStart(LocalDateTime.now().withNano(0).minusHours(1));
        presale.setPresaleEnd(LocalDateTime.now().withNano(0).plusDays(1));
        presale.setMaxReservations(maxReservations);
        presale.setReservationCount(0);
        presale.setStatus(1);
        presaleMapper.insert(presale);
        return presale.getId();
    }

    private void runConcurrently(int threads, java.util.function.IntConsumer action) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.accept(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
