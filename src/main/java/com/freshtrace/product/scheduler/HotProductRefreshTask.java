package com.freshtrace.product.scheduler;

import com.freshtrace.product.service.ProductHotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热门商品缓存刷新任务（Phase 2.15）。
 * <p>
 * 默认每 5 分钟按销量重建 Top100 ZSET；通过 product.hot.enabled 开关控制（测试环境关闭以减少噪声）。
 */
@Component
@ConditionalOnProperty(name = "product.hot.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class HotProductRefreshTask {

    private final ProductHotService productHotService;

    @Scheduled(fixedDelayString = "${product.hot.refresh-interval-ms:300000}",
            initialDelayString = "${product.hot.initial-delay-ms:60000}")
    public void refresh() {
        try {
            productHotService.refreshHotProducts();
        } catch (Exception e) {
            log.error("refresh hot products failed", e);
        }
    }
}
