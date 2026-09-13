package com.freshtrace.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端操作日志视图。
 */
@Data
public class OperationLogVO {

    private Long id;

    private Long operatorId;

    /** 操作人昵称/用户名 */
    private String operatorName;

    private String targetType;

    private Long targetId;

    private String action;

    /** 参数摘要(JSON) */
    private String detail;

    private String ipAddress;

    private LocalDateTime createTime;
}
