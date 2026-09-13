package com.freshtrace.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员处理举报参数。
 */
@Data
public class ReportHandleDTO {

    /** 处理结论：1=有效,2=驳回 */
    @NotNull(message = "处理结论不能为空")
    private Integer status;

    @Size(max = 500, message = "处理意见过长")
    private String handleReason;
}
