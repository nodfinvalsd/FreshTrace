package com.freshtrace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.scheduler.PresaleExpireTask;
import com.freshtrace.presale.service.PresaleExpireService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预售到期处理集成测试（Phase 7 Day 3）：
 * 到期推进状态/商品转销售中/标记通知 + 幂等 + 未到期不处理 + 已关闭不处理 + 定时兜底。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PresaleExpireIntegrationTest {

    @Autowired
    private PresaleExpireService presaleExpireService;

    @Autowired
    private PresaleExpireTask presaleExpireTask;

    @Autowired
    private PresaleMapper presaleMapper;

    @Autowired
    private PresaleReservationMapper presaleReservationMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SpuMapper spuMapper;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void expireTransitionsPresaleProductAndMarksNotified() {
        long productId = createProduct(1);
        long presaleId = createPresale(productId, 1, LocalDateTime.now().withNano(0).minusMinutes(1));
        addReservation(presaleId, 700001L);
        addReservation(presaleId, 700002L);

        presaleExpireService.expireIfDue(presaleId);

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(3);
        assertThat(notifiedCount(presaleId)).isEqualTo(2);
    }

    @Test
    void expireIsIdempotent() {
        long productId = createProduct(1);
        long presaleId = createPresale(productId, 1, LocalDateTime.now().withNano(0).minusMinutes(1));
        addReservation(presaleId, 700003L);

        presaleExpireService.expireIfDue(presaleId);
        presaleExpireService.expireIfDue(presaleId);

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(3);
        assertThat(notifiedCount(presaleId)).isEqualTo(1);
    }

    @Test
    void notDuePresaleNotExpired() {
        long productId = createProduct(1);
        long presaleId = createPresale(productId, 1, LocalDateTime.now().withNano(0).plusDays(1));

        presaleExpireService.expireIfDue(presaleId);

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(1);
    }

    @Test
    void closedPresaleNotExpired() {
        long productId = createProduct(1);
        long presaleId = createPresale(productId, 0, LocalDateTime.now().withNano(0).minusMinutes(1));

        presaleExpireService.expireIfDue(presaleId);

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isZero();
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(1);
    }

    @Test
    void scheduledScanExpiresDuePresales() {
        long productId = createProduct(1);
        long presaleId = createPresale(productId, 1, LocalDateTime.now().withNano(0).minusMinutes(1));

        presaleExpireTask.scanDue();

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(3);
    }

    private long notifiedCount(long presaleId) {
        return presaleReservationMapper.selectCount(new LambdaQueryWrapper<PresaleReservation>()
                .eq(PresaleReservation::getPresaleId, presaleId)
                .eq(PresaleReservation::getNotified, 1));
    }

    private long createProduct(int lifecycle) {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(1L);
        product.setTitle("到期测试商品");
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(100);
        product.setUnit("斤");
        product.setMainImage("http://img/x.png");
        product.setLifecycle(lifecycle);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long createPresale(long productId, int status, LocalDateTime end) {
        Presale presale = new Presale();
        presale.setProductId(productId);
        presale.setFarmerId(1L);
        presale.setPresaleStart(end.minusDays(2));
        presale.setPresaleEnd(end);
        presale.setMaxReservations(0);
        presale.setReservationCount(0);
        presale.setStatus(status);
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
