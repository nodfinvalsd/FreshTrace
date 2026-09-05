package com.freshtrace.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateDTO {

    @NotNull(message = "所属SPU不能为空")
    private Long spuId;

    @NotBlank(message = "商品标题不能为空")
    @Size(max = 200, message = "商品标题长度不能超过 200")
    private String title;

    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    private Integer stock;

    @Size(max = 20, message = "单位长度不能超过 20")
    private String unit;

    @Size(max = 500, message = "主图URL长度不能超过 500")
    private String mainImage;

    @Min(value = 0, message = "生命周期状态不能小于 0")
    @Max(value = 4, message = "生命周期状态不能大于 4")
    private Integer lifecycle;

    @Valid
    private List<ProductAttributeDTO> attributes;

    @Valid
    private List<ProductImageDTO> images;
}
