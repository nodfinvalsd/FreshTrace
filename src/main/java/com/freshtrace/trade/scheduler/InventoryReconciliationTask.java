package com.freshtrace.trade.scheduler;

import com.freshtrace.trade.service.InventoryReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 库存对账定时任务（默认关闭，测试环境不启动）。
 * <p>
 * - 通过 inventory.reconciliation.enabled 开关控制 Bean 创建；
 * - AtomicBoolean 防止单实例内任务重叠（本轮未完成则跳过下一轮）；
 * - 多实例安全留待后续（Deferred），当前不引入分布式锁。
 */
@Component
@ConditionalOnProperty(name = "inventory.reconciliation.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InventoryReconciliationTask {

    private final InventoryReconciliationService inventoryReconciliationService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${inventory.reconciliation.interval-ms:300000}")
    public void reconcile() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Inventory reconciliation is still running, skip this round");
            return;
        }
        try {
            inventoryReconciliationService.scanAndRepair();
        } catch (Exception e) {
            log.error("Inventory reconciliation task failed", e);
        } finally {
            running.set(false);
        }
    }
}
