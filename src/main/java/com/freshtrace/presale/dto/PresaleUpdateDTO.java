package com.freshtrace.presale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 修改预售请求。绑定商品不可变更（1:1），仅允许调整时间窗口与预约上限。
 */
@Data
public class PresaleUpdateDTO {

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
