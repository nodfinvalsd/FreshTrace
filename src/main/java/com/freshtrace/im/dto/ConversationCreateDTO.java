package com.freshtrace.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求：买家从商品详情页发起。
 * <p>
 * 买家身份取自登录态（UserContext），此处只需指定会话对端果农与来源商品。
 */
@Data
public class ConversationCreateDTO {

    /** 目标果农ID（t_farmer.id），必须已通过认证 */
    @NotNull(message = "果农ID不能为空")
    private Long farmerId;

    /** 来源商品，可空；不为空时校验是否属于该果农 */
    private Long productId;
}
