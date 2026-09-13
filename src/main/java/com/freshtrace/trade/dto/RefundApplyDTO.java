package com.freshtrace.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RefundApplyDTO {

    @NotBlank(message = "退款原因不能为空")
    @Size(max = 500, message = "退款原因过长")
    private String reason;

    /** 售后凭证图片，Phase 10 收货后退款场景使用 */
    @Size(max = 9, message = "凭证图片最多9张")
    private List<String> evidenceImages;
}
