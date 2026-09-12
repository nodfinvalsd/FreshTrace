package com.freshtrace.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布动态请求。果农身份由服务端上下文推导，不接受客户端提交 farmerId。
 * 可选关联商品/溯源节点，关联对象必须归属当前果农（Service 校验）。
 */
@Data
public class PostCreateDTO {

    @NotBlank(message = "动态内容不能为空")
    @Size(max = 2000, message = "动态内容过长")
    private String content;

    /** 关联商品ID，可为空 */
    private Long productId;

    /** 关联溯源节点ID，可为空 */
    private Long traceNodeId;

    /** 图片 URL 列表，可为空 */
    @Size(max = 9, message = "图片数量不能超过9张")
    private List<String> images;
}
