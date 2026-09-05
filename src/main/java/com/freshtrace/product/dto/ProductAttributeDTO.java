package com.freshtrace.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductAttributeDTO {

    @NotBlank(message = "属性名不能为空")
    @Size(max = 50, message = "属性名长度不能超过 50")
    private String attrName;

    @NotBlank(message = "属性值不能为空")
    @Size(max = 255, message = "属性值长度不能超过 255")
    private String attrValue;

    private BigDecimal extraPrice;
}
