package com.freshtrace;

import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.scheduler.PresaleCountReconciliationTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预售预约计数对账测试（Phase 7 Day 4）：
 * 以 COUNT(*) 为权威，修正 t_presale.reservation_count 冗余列并刷新 Redis 限流计数。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PresaleCountReconciliationTest {

    @Autowired
    private PresaleCountReconciliationTask presaleCountReconciliationTask;

    @Autowired
    private PresaleMapper presaleMapper;

    @Autowired
    private PresaleReservationMapper presaleReservationMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void reconcileFixesDbRedundantCountAndRedis() {
        long presaleId = createPresale();
        addReservation(presaleId, 1L);
        addReservation(presaleId, 2L);
        stringRedisTemplate.opsForValue().set(CacheKeys.presaleCount(presaleId), "99");

        presaleCountReconciliationTask.reconcile();

        assertThat(presaleMapper.selectById(presaleId).getReservationCount()).isEqualTo(2);
        assertThat(stringRedisTemplate.opsForValue().get(CacheKeys.presaleCount(presaleId))).isEqualTo("2");
    }

    private long createPresale() {
        Presale presale = new Presale();
        presale.setProductId(seq.incrementAndGet());
        presale.setFarmerId(1L);
        presale.setPresaleStart(LocalDateTime.now().withNano(0).minusHours(1));
        presale.setPresaleEnd(LocalDateTime.now().withNano(0).plusDays(1));
        presale.setMaxReservations(0);
        presale.setReservationCount(0);
        presale.setStatus(1);
        presaleMapper.insert(presale);
        return presale.getId();
    }

    private void addReservation(long presaleId, long userId) {
        PresaleReservation reservation = new PresaleReservation();
        reservation.setPresaleId(presaleId);
        reservation.setUserId(userId);
        reservation.setQuantity(1);
        reservation.setNotified(0);
        presaleReservationMapper.insert(reservation);
    }
}
