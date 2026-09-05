package com.freshtrace.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateDTO extends ProductCreateDTO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
}
