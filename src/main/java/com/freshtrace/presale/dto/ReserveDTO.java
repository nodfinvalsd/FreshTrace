package com.freshtrace.presale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 预约请求。V1 为免费预约（无定金、无支付、不生成订单），仅表达成熟提醒意向。
 */
@Data
public class ReserveDTO {

    @Min(value = 1, message = "预约数量必须大于0")
    @Max(value = 99, message = "单次预约数量不能超过99")
    private Integer quantity = 1;
}
