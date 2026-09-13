package com.freshtrace.trade.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 果农处理退款申请参数（同意/拒绝共用）。
 */
@Data
public class RefundHandleDTO {

    /** 处理意见；拒绝时建议填写 */
    @Size(max = 500, message = "处理意见过长")
    private String handleReason;
}
