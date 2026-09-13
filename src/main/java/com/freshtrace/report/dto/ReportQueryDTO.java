package com.freshtrace.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端举报列表查询参数。
 */
@Data
public class ReportQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 处理状态过滤：0=待处理,1=已处理,2=已驳回；为空查全部 */
    private Integer status;

    /** 举报对象类型过滤：1=溯源信息,2=商品品质,3=果农行为；为空查全部 */
    private Integer targetType;
}
