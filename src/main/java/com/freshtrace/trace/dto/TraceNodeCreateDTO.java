package com.freshtrace.trace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 新增溯源节点请求。果农身份由服务端上下文推导，不接受客户端提交 farmerId。
 */
@Data
public class TraceNodeCreateDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "节点类型不能为空")
    private Integer nodeType;

    @NotBlank(message = "节点标题不能为空")
    @Size(max = 100, message = "节点标题过长")
    private String title;

    private String description;

    /** 图片 URL 列表，可为空 */
    @Size(max = 9, message = "图片数量不能超过9张")
    private List<String> images;

    @NotNull(message = "发生日期不能为空")
    private LocalDate occurredAt;
}
