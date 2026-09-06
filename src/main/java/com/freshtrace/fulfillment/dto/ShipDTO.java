package com.freshtrace.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 果农发货请求。物流信息由果农填写，长度上限与 t_sub_order 列定义一致。
 */
@Data
public class ShipDTO {

    @NotBlank(message = "物流公司不能为空")
    @Size(max = 100, message = "物流公司名称过长")
    private String logisticsCompany;

    @NotBlank(message = "物流单号不能为空")
    @Size(max = 100, message = "物流单号过长")
    private String logisticsNo;
}
