package com.freshtrace.report.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端举报视图。
 */
@Data
public class ReportVO {

    private Long id;

    private Long userId;

    /** 举报人昵称/用户名 */
    private String reporterName;

    private Integer targetType;

    private String targetTypeDesc;

    private Long targetId;

    private String reason;

    private List<String> evidenceImages;

    private Integer status;

    private String statusDesc;

    private Long handlerId;

    private String handleReason;

    private LocalDateTime handledAt;

    private LocalDateTime createTime;
}
