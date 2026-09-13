package com.freshtrace.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端退款工单列表查询参数。
 */
@Data
public class RefundQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 退款状态过滤：0=待处理,1=果农同意,2=果农拒绝,3=客服仲裁,4=已退款,5=已驳回；为空查全部 */
    private Integer status;
}
