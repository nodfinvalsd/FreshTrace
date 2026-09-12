package com.freshtrace.presale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.enums.PresaleStatus;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.service.PresaleExpireService;
import com.freshtrace.presale.support.PresaleMessageSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预售到期实现（Phase 7 Day 3）。
 * <p>
 * 并发/幂等裁决：`UPDATE t_presale SET status=2 WHERE id=? AND status=1` 条件更新抢占，
 * 与 Phase 3/4 状态流转同构；MQ 与定时任务撞车时仅一个成功推进，其余 no-op。
 */
@Service
@Slf4j
public class PresaleExpireServiceImpl implements PresaleExpireService {

    private final PresaleMapper presaleMapper;
    private final PresaleReservationMapper presaleReservationMapper;
    private final ProductService productService;
    private final PresaleMessageSupport presaleMessageSupport;
    private final TransactionTemplate transactionTemplate;

    public PresaleExpireServiceImpl(PresaleMapper presaleMapper,
                                    PresaleReservationMapper presaleReservationMapper,
                                    ProductService productService,
                                    PresaleMessageSupport presaleMessageSupport,
                                    PlatformTransactionManager transactionManager) {
        this.presaleMapper = presaleMapper;
        this.presaleReservationMapper = presaleReservationMapper;
        this.productService = productService;
        this.presaleMessageSupport = presaleMessageSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record ExpireContext(Long presaleId, Long productId, List<Long> userIds) {
    }

    @Override
    public void expireIfDue(Long presaleId) {
        Presale presale = presaleMapper.selectById(presaleId);
        if (presale == null || PresaleStatus.fromCode(presale.getStatus()) != PresaleStatus.ONGOING) {
            return;
        }
        if (presale.getPresaleEnd() != null && LocalDateTime.now().isBefore(presale.getPresaleEnd())) {
            // 消息提前触发（如后续延长了 presale_end）→ 重投剩余延迟，避免预售被过早结束
            presaleMessageSupport.sendExpire(presale.getId(), presale.getProductId(), presale.getPresaleEnd());
            return;
        }

        ExpireContext context = transactionTemplate.execute(status -> expireTx(presale));
        if (context == null || context.userIds().isEmpty()) {
            return;
        }
        presaleMessageSupport.sendMatureNotification(context.presaleId(), context.productId(), context.userIds());
    }

    private ExpireContext expireTx(Presale presale) {
        int rows = presaleMapper.update(null, new LambdaUpdateWrapper<Presale>()
                .eq(Presale::getId, presale.getId())
                .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                .set(Presale::getStatus, PresaleStatus.ENDED.getCode()));
        if (rows == 0) {
            return null;
        }

        productService.markPresaleEnded(presale.getProductId());

        List<PresaleReservation> pending = presaleReservationMapper.selectList(
                new LambdaQueryWrapper<PresaleReservation>()
                        .eq(PresaleReservation::getPresaleId, presale.getId())
                        .eq(PresaleReservation::getNotified, 0));
        if (pending.isEmpty()) {
            return new ExpireContext(presale.getId(), presale.getProductId(), List.of());
        }

        List<Long> userIds = pending.stream().map(PresaleReservation::getUserId).toList();
        presaleReservationMapper.update(null, new LambdaUpdateWrapper<PresaleReservation>()
                .eq(PresaleReservation::getPresaleId, presale.getId())
                .eq(PresaleReservation::getNotified, 0)
                .set(PresaleReservation::getNotified, 1));
        return new ExpireContext(presale.getId(), presale.getProductId(), userIds);
    }
}
