package com.freshtrace.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 评价列表查询参数。productId 与 farmerId 至少传其一，都传时按 AND 过滤。
 */
@Data
public class ReviewQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size = 10;

    /** 按商品查询 */
    private Long productId;

    /** 按果农查询 */
    private Long farmerId;
}
