package com.freshtrace.presale.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.entity.PresaleReservation;
import com.freshtrace.presale.enums.PresaleStatus;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.mapper.PresaleReservationMapper;
import com.freshtrace.presale.support.PresaleCountSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 预售预约计数对账（Phase 7 Day 2）。
 * <p>
 * 权威源为 {@code COUNT(*) FROM t_presale_reservation}：
 * <ul>
 *     <li>修正 {@code t_presale.reservation_count} 冗余列；</li>
 *     <li>刷新 Redis 限流计数，修复 INCR/补偿造成的偏差。</li>
 * </ul>
 * AtomicBoolean 仅防止单实例任务重叠；多实例安全留待后续（Deferred）。
 */
@Component
@ConditionalOnProperty(name = "presale.count-reconciliation.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PresaleCountReconciliationTask {

    private static final int BATCH_SIZE = 200;

    private final PresaleMapper presaleMapper;
    private final PresaleReservationMapper presaleReservationMapper;
    private final PresaleCountSupport presaleCountSupport;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${presale.count-reconciliation.interval-ms:300000}",
            initialDelayString = "${presale.count-reconciliation.interval-ms:300000}")
    public void reconcile() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Presale count reconciliation is still running, skip this round");
            return;
        }
        try {
            long fixed = 0;
            long pageNo = 1;
            while (true) {
                Page<Presale> page = new Page<>(pageNo, BATCH_SIZE);
                presaleMapper.selectPage(page, new LambdaQueryWrapper<Presale>()
                        .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                        .orderByAsc(Presale::getId));
                for (Presale presale : page.getRecords()) {
                    try {
                        long count = presaleReservationMapper.selectCount(
                                new LambdaQueryWrapper<PresaleReservation>()
                                        .eq(PresaleReservation::getPresaleId, presale.getId()));
                        if (presale.getReservationCount() == null
                                || presale.getReservationCount().longValue() != count) {
                            presaleMapper.update(null, new LambdaUpdateWrapper<Presale>()
                                    .eq(Presale::getId, presale.getId())
                                    .set(Presale::getReservationCount, count));
                            fixed++;
                        }
                        presaleCountSupport.sync(presale.getId(), (int) count);
                    } catch (Exception e) {
                        // 单条失败不阻塞本轮其余预售（下轮重扫兜底）
                        log.error("presale count reconcile failed, presaleId={}", presale.getId(), e);
                    }
                }
                if (page.getRecords().size() < BATCH_SIZE) {
                    break;
                }
                pageNo++;
            }
            log.info("presale count reconcile finished, fixed={}", fixed);
        } catch (Exception e) {
            log.error("presale count reconcile task failed", e);
        } finally {
            running.set(false);
        }
    }
}
