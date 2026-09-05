package com.freshtrace.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CartAddDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;

    /**
     * 规格选择快照(JSON数组)，如 [{"name":"规格","value":"10斤装"}]；无规格时不传或传空串。
     * 服务端会与 t_product_attribute 比对校验，并重新序列化为固定键序的规范形式。
     */
    @Size(max = 500, message = "规格快照过长")
    private String specSnapshot;
}
