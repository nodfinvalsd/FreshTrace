package com.freshtrace.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageDTO {

    @NotBlank(message = "图片URL不能为空")
    @Size(max = 500, message = "图片URL长度不能超过 500")
    private String imageUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
}
