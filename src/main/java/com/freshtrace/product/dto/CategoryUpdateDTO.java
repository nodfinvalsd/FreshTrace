package com.freshtrace.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryUpdateDTO {

    @NotBlank(message = "品类名称不能为空")
    @Size(max = 50, message = "品类名称长度不能超过 50")
    private String name;

    @Size(max = 500, message = "图标URL长度不能超过 500")
    private String iconUrl;

    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
}
