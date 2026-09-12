package com.freshtrace.community.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 动态列表查询参数。farmerId 用于筛选某果农的动态（果农主页）。
 */
@Data
public class PostQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size = 10;

    /** 按果农筛选，可为空 */
    private Long farmerId;
}
