package com.freshtrace.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发表评价请求。买家身份由服务端上下文推导，不接受客户端提交 userId/farmerId。
 */
@Data
public class ReviewCreateDTO {

    @NotBlank(message = "子订单号不能为空")
    private String subOrderNo;

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer rating;

    @Size(max = 1000, message = "评价内容过长")
    private String content;

    /** 图片 URL 列表，可为空 */
    @Size(max = 9, message = "图片数量不能超过9张")
    private List<String> images;
}
