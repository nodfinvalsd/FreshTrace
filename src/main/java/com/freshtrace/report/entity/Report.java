package com.freshtrace.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户举报（Phase 10）。举报溯源不实、商品品质问题、果农违规行为等，由管理员审核处理。
 */
@TableName("t_report")
@Data
@EqualsAndHashCode(callSuper = true)
public class Report extends BaseEntity {

    /** 举报人 */
    private Long userId;

    /** 举报对象类型：1=溯源信息,2=商品品质,3=果农行为 */
    private Integer targetType;

    /** 举报对象ID */
    private Long targetId;

    /** 举报原因 */
    private String reason;

    /** 凭证图片(JSON 数组) */
    private String evidenceImages;

    /** 处理状态：0=待处理,1=已处理(有效),2=已驳回(无效) */
    private Integer status;

    /** 处理人ID(管理员) */
    private Long handlerId;

    /** 处理意见 */
    private String handleReason;

    private LocalDateTime handledAt;
}
