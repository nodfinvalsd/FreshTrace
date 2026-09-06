package com.freshtrace.trace.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 溯源节点展示 VO。仅包含溯源展示所需信息，不携带录入账号等系统字段。
 */
@Data
public class TraceNodeVO {

    private Long id;

    private Long productId;

    private Integer nodeType;

    private String title;

    private String description;

    private List<String> images;

    private LocalDate occurredAt;
}
