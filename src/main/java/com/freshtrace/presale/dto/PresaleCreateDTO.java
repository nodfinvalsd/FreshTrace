package com.freshtrace.presale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设置预售请求。果农身份由服务端上下文推导，不接受客户端提交 farmerId。
 */
@Data
public class PresaleCreateDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "预售开始时间不能为空")
    private LocalDateTime presaleStart;

    @NotNull(message = "预售截止时间不能为空")
    private LocalDateTime presaleEnd;

    /** 预计成熟采摘时间，可为空 */
    private LocalDateTime expectedHarvest;

    /** 最大预约数，0=不限，缺省为 0 */
    @Min(value = 0, message = "最大预约数不能为负数")
    private Integer maxReservations;
}
