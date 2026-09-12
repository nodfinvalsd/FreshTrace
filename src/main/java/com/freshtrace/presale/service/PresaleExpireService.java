package com.freshtrace.presale.service;

/**
 * 预售到期处理（Phase 7 Day 3）。
 * <p>
 * MQ 延迟消息与定时兜底共用此入口，必须幂等：
 * - 预售不存在 / 非进行中 → no-op；
 * - 未到 presale_end（消息提前触发）→ 重投剩余延迟；
 * - 到期 → 状态推进 ENDED + 商品转销售中 + 批量通知预约用户。
 */
public interface PresaleExpireService {

    void expireIfDue(Long presaleId);
}
