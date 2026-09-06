package com.freshtrace.trace.vo;

import lombok.Data;

/**
 * 溯源模板节点 VO。仅包含模板预填所需信息，与 TraceNodeVO（真实溯源记录）区分。
 */
@Data
public class TraceTemplateNodeVO {

    private Integer nodeType;

    private String title;

    private String description;
}
