package com.freshtrace.farmer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端果农认证审核列表查询参数。
 */
@Data
public class FarmerAuditQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 认证状态过滤：0=待审核,1=通过,2=驳回；为空查全部 */
    private Integer auditStatus;
}
