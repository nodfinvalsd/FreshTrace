package com.freshtrace;

import com.freshtrace.product.entity.Product;
import com.freshtrace.product.entity.Spu;
import com.freshtrace.product.mapper.ProductMapper;
import com.freshtrace.product.mapper.SpuMapper;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.service.PresaleExpireService;
import com.freshtrace.presale.support.PresaleMessageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 预售到期「提前触发重投」测试（Phase 7 Day 4）：
 * 延迟消息可能因预售截止时间被延后而提前到达，此时不应结束预售，而是重投剩余延迟。
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PresaleExpireRescheduleTest {

    @Autowired
    private PresaleExpireService presaleExpireService;

    @Autowired
    private PresaleMapper presaleMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SpuMapper spuMapper;

    @MockitoBean
    private PresaleMessageSupport presaleMessageSupport;

    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());

    @Test
    void notDueReschedulesAndKeepsOngoing() {
        long productId = createProduct();
        long presaleId = createPresale(productId, LocalDateTime.now().withNano(0).plusDays(1));

        presaleExpireService.expireIfDue(presaleId);

        Presale presale = presaleMapper.selectById(presaleId);
        assertThat(presale.getStatus()).isEqualTo(1);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(1);
        verify(presaleMessageSupport).sendExpire(presaleId, productId, presale.getPresaleEnd());
    }

    @Test
    void dueExpireSendsMatureNotificationNotReschedule() {
        long productId = createProduct();
        long presaleId = createPresale(productId, LocalDateTime.now().withNano(0).minusMinutes(1));

        presaleExpireService.expireIfDue(presaleId);

        assertThat(presaleMapper.selectById(presaleId).getStatus()).isEqualTo(2);
        assertThat(productMapper.selectById(productId).getLifecycle()).isEqualTo(3);
        verify(presaleMessageSupport, never()).sendExpire(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private long createProduct() {
        long n = seq.incrementAndGet();
        Spu spu = new Spu();
        spu.setCategoryId(1L);
        spu.setName("芒果" + n);
        spu.setStatus(1);
        spuMapper.insert(spu);

        Product product = new Product();
        product.setSpuId(spu.getId());
        product.setFarmerId(1L);
        product.setTitle("重投测试商品");
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(100);
        product.setUnit("斤");
        product.setMainImage("http://img/x.png");
        product.setLifecycle(1);
        product.setAuditStatus(1);
        product.setSalesCount(0);
        product.setViewCount(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long createPresale(long productId, LocalDateTime end) {
        Presale presale = new Presale();
        presale.setProductId(productId);
        presale.setFarmerId(1L);
        presale.setPresaleStart(end.minusDays(2));
        presale.setPresaleEnd(end);
        presale.setMaxReservations(0);
        presale.setReservationCount(0);
        presale.setStatus(1);
        presaleMapper.insert(presale);
        return presale.getId();
    }
}
