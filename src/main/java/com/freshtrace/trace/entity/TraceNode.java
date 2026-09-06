package com.freshtrace.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 溯源节点。t_product 描述「这个水果是什么」，t_trace_node 描述「这个水果发生过什么」。
 * <p>
 * 一个商品对应多个溯源节点，按 occurred_at 升序形成公开溯源时间线。
 * 审计设计：occurred_at(业务事件发生日期) 与 create_time(录入时间) 分离，
 * 保留真实业务时间与系统录入时间的差异，可作为数据可信度信号。
 * <p>
 * Phase 5 Day 1 仅建表与持久化层准备，节点增删改/时间线查询后续实现。
 */
@TableName("t_trace_node")
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceNode extends BaseEntity {

    private Long productId;

    private Integer nodeType;

    private String title;

    private String description;

    private String images;

    private LocalDate occurredAt;
}
