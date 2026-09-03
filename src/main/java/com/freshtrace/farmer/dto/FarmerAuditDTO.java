package com.freshtrace.farmer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FarmerAuditDTO {

    @NotNull(message = "果农ID不能为空")
    private Long farmerId;

    @NotNull(message = "审核状态不能为空")
    private Integer auditStatus;

    private String auditReason;
}
