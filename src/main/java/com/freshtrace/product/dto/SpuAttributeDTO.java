package com.freshtrace.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SpuAttributeDTO {

    @NotBlank(message = "属性名不能为空")
    @Size(max = 50, message = "属性名长度不能超过 50")
    private String attrName;

    @NotEmpty(message = "属性可选值不能为空")
    private List<String> attrValues;

    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
}
