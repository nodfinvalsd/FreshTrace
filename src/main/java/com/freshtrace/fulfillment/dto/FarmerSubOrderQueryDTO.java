package com.freshtrace.fulfillment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 果农子订单列表查询参数。果农身份绝不来自客户端，由 FarmerContext 注入。
 */
@Data
public class FarmerSubOrderQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size = 10;

    /** SubOrderStatus code，可选；不传查全部，传非法值返回参数错误 */
    private Integer status;
}
