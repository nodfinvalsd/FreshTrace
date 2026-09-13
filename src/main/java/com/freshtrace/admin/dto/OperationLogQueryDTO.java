package com.freshtrace.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 管理端操作日志查询参数。时间段按操作时间(createTime)过滤，左闭右闭。
 */
@Data
public class OperationLogQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 操作人ID过滤 */
    private Long operatorId;

    /** 操作对象类型过滤（FARMER / PRODUCT / CATEGORY / SPU / REFUND / REPORT ...） */
    private String targetType;

    /** 操作动作过滤 */
    private String action;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
