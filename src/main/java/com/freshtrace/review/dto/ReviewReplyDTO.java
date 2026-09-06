package com.freshtrace.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 果农回复评价请求。
 */
@Data
public class ReviewReplyDTO {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容过长")
    private String reply;
}
