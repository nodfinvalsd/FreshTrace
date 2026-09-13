package com.freshtrace.trade.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员退款仲裁参数。
 */
@Data
public class RefundArbitrateDTO {

    /** true=同意退款(平台垫付/判定成立)，false=驳回退款 */
    @NotNull(message = "仲裁结论不能为空")
    private Boolean approve;

    /** 仲裁意见 */
    @Size(max = 500, message = "仲裁意见过长")
    private String handleReason;
}
