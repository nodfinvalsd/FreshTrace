package com.freshtrace.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductAuditDTO {

    @NotNull(message = "审核结果不能为空")
    private Integer auditStatus;

    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String auditReason;
}
