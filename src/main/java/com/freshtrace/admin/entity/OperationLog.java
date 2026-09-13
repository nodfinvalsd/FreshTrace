package com.freshtrace.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（追加型审计表）。
 * <p>
 * 不继承 BaseEntity：日志只增不改不删，无需 update_time / deleted；
 * create_time 由数据库默认值填充，避免依赖自动填充。
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人ID */
    private Long operatorId;

    /** 操作对象类型 */
    private String targetType;

    /** 操作对象ID */
    private Long targetId;

    /** 操作动作 */
    private String action;

    /** 参数摘要(JSON) */
    private String detail;

    /** 操作IP */
    private String ipAddress;

    /** 操作时间 */
    private LocalDateTime createTime;
}
