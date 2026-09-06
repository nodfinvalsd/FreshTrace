package com.freshtrace.fulfillment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.fulfillment.service.FulfillmentService;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.SubOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自动确认收货定时兜底（Phase 4 Day 2）。
 * <p>
 * - 每 5 分钟扫描 status=PENDING_RECEIVE 且 shipped_at <= now-7 天的子订单，逐条调用
 *   {@link FulfillmentService#autoConfirmSubOrder}，与 MQ 延迟消息共用同一确认逻辑；
 * - 分页扫描避免一次性加载全部过期订单；
 * - 并发安全：与 MQ 路径撞车时由数据库条件更新（WHERE status=PENDING_RECEIVE）最终仲裁，
 *   本任务无需分布式锁；AtomicBoolean 仅防止单实例任务重叠。
 */
@Component
@ConditionalOnProperty(name = "fulfillment.auto-confirm.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AutoConfirmTask {

    /** 每批扫描条数（MyBatis-Plus 全局 maxLimit=500） */
    private static final int BATCH_SIZE = 200;

    /** 发货后自动确认收货天数 */
    private static final int AUTO_CONFIRM_DAYS = 7;

    private final SubOrderMapper subOrderMapper;
    private final FulfillmentService fulfillmentService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${fulfillment.auto-confirm.interval-ms:300000}",
            initialDelayString = "${fulfillment.auto-confirm.interval-ms:300000}")
    public void autoConfirmExpired() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Auto confirm task is still running, skip this round");
            return;
        }
        try {
            LocalDateTime deadline = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
            long confirmed = 0;
            long pageNo = 1;
            while (true) {
                Page<SubOrder> page = new Page<>(pageNo, BATCH_SIZE);
                subOrderMapper.selectPage(page, new LambdaQueryWrapper<SubOrder>()
                        .eq(SubOrder::getStatus, SubOrderStatus.PENDING_RECEIVE.getCode())
                        .le(SubOrder::getShippedAt, deadline)
                        .orderByAsc(SubOrder::getId));
                for (SubOrder subOrder : page.getRecords()) {
                    try {
                        fulfillmentService.autoConfirmSubOrder(subOrder.getSubOrderNo());
                        confirmed++;
                    } catch (Exception e) {
                        // 单条失败不阻塞本轮其余订单（基础设施异常下一条继续，下轮重扫兜底）
                        log.error("auto confirm failed, subOrderNo={}", subOrder.getSubOrderNo(), e);
                    }
                }
                if (page.getRecords().size() < BATCH_SIZE) {
                    break;
                }
                pageNo++;
            }
            log.info("auto confirm task finished, confirmed={}", confirmed);
        } catch (Exception e) {
            log.error("auto confirm task failed", e);
        } finally {
            running.set(false);
        }
    }
}
