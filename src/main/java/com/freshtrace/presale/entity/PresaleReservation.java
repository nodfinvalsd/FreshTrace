package com.freshtrace.presale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 预售预约。同一用户对同一预售仅一条：UNIQUE(presale_id, user_id)。
 * <p>
 * V1 为免费预约（无定金、无支付、无订单），仅表达「成熟提醒我」的意向。
 */
@TableName("t_presale_reservation")
@Data
@EqualsAndHashCode(callSuper = true)
public class PresaleReservation extends BaseEntity {

    private Long presaleId;

    private Long userId;

    private Integer quantity;

    /** 成熟后是否已通知 0=否 1=是 */
    private Integer notified;
}
