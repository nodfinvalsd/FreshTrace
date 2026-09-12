package com.freshtrace.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论请求。评论人身份由服务端上下文推导，不接受客户端提交 userId。
 * parentId 为空表示顶层评论，非空表示对某条评论的回复（嵌套）。
 */
@Data
public class CommentCreateDTO {

    /** 父评论ID，可为空（顶层评论） */
    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容过长")
    private String content;
}
