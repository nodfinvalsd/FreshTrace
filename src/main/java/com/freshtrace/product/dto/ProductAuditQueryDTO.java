package com.freshtrace.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端商品审核列表查询参数。
 */
@Data
public class ProductAuditQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 审核状态过滤：0=待审核,1=通过,2=驳回；为空查全部 */
    private Integer auditStatus;
}
