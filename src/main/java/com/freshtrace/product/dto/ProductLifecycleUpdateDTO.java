package com.freshtrace.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductLifecycleUpdateDTO {

    @NotNull(message = "生命周期状态不能为空")
    @Min(value = 0, message = "生命周期状态不能小于 0")
    @Max(value = 4, message = "生命周期状态不能大于 4")
    private Integer lifecycle;
}
