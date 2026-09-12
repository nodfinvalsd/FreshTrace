package com.freshtrace.presale.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.presale.entity.Presale;
import com.freshtrace.presale.enums.PresaleStatus;
import com.freshtrace.presale.mapper.PresaleMapper;
import com.freshtrace.presale.service.PresaleExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 预售到期定时兜底（Phase 7 Day 3）。
 * <p>
 * 每 5 分钟扫描 status=ONGOING 且 presale_end &lt;= now 的预售，逐条调用
 * {@link PresaleExpireService#expireIfDue}，与 MQ 延迟消息共用同一到期逻辑。
 * 采用游标（id &gt; lastId）分页：本任务会将命中行状态推进为 ENDED 从而使其离开过滤集，
 * 偏移分页会漏行，游标分页对集合变更安全。
 */
@Component
@ConditionalOnProperty(name = "presale.expire.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PresaleExpireTask {

    private static final int BATCH_SIZE = 200;

    private final PresaleMapper presaleMapper;
    private final PresaleExpireService presaleExpireService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${presale.expire.interval-ms:300000}",
            initialDelayString = "${presale.expire.interval-ms:300000}")
    public void scanDue() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Presale expire task is still running, skip this round");
            return;
        }
        try {
            long processed = 0;
            long lastId = 0L;
            while (true) {
                Page<Presale> page = new Page<>(1, BATCH_SIZE);
                presaleMapper.selectPage(page, new LambdaQueryWrapper<Presale>()
                        .eq(Presale::getStatus, PresaleStatus.ONGOING.getCode())
                        .le(Presale::getPresaleEnd, LocalDateTime.now())
                        .gt(Presale::getId, lastId)
                        .orderByAsc(Presale::getId));
                if (page.getRecords().isEmpty()) {
                    break;
                }
                for (Presale presale : page.getRecords()) {
                    try {
                        presaleExpireService.expireIfDue(presale.getId());
                        processed++;
                    } catch (Exception e) {
                        // 单条失败不阻塞本轮其余预售（下轮重扫兜底）
                        log.error("presale expire failed, presaleId={}", presale.getId(), e);
                    }
                    lastId = presale.getId();
                }
                if (page.getRecords().size() < BATCH_SIZE) {
                    break;
                }
            }
            log.info("presale expire scan finished, processed={}", processed);
        } catch (Exception e) {
            log.error("presale expire task failed", e);
        } finally {
            running.set(false);
        }
    }
}
