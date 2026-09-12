package com.freshtrace.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(10000, "参数错误"),
    NOT_FOUND(10001, "资源不存在"),
    BIZ_ERROR(10002, "业务处理失败"),

    UNAUTHORIZED(21001, "未登录或登录已过期"),
    FORBIDDEN(21002, "无权限访问"),

    USERNAME_EXISTS(30001, "用户名已存在"),
    PHONE_EXISTS(30002, "手机号已存在"),
    LOGIN_FAILED(30003, "用户名或密码错误"),
    USER_DISABLED(30004, "账号已被禁用"),
    TOKEN_NOT_REFRESHABLE(30005, "Token 未到可刷新时间"),
    ADDRESS_NOT_FOUND(30006, "地址不存在"),
    FARMER_ALREADY_EXISTS(30007, "已提交果农认证申请"),
    FARMER_NOT_EXISTS(30008, "尚未提交果农认证申请"),
    FARMER_AUDIT_STATUS_INVALID(30009, "审核状态非法"),
    FARMER_AUDIT_NOT_PENDING(30010, "该认证已审核，不能重复审核"),

    CATEGORY_NOT_FOUND(30011, "品类不存在"),
    CATEGORY_NAME_DUPLICATE(30012, "品类名称已存在"),
    CATEGORY_DELETE_FAILED(30013, "该品类下存在 SPU，无法删除"),
    SPU_NOT_FOUND(30014, "SPU 不存在"),
    SPU_CATEGORY_NOT_FOUND(30015, "SPU 所属品类不存在"),

    PRODUCT_NOT_FOUND(30016, "商品不存在"),
    PRODUCT_SPU_NOT_FOUND(30017, "商品关联的 SPU 不存在"),
    PRODUCT_PERMISSION_DENIED(30018, "无权操作该商品"),
    PRODUCT_STATUS_INVALID(30019, "商品状态转换不合法"),
    PRODUCT_AUDIT_NOT_PENDING(30020, "该商品已审核，不能重复审核"),
    PRODUCT_AUDIT_REASON_REQUIRED(30021, "驳回时必须填写审核意见"),
    PRODUCT_VERSION_CONFLICT(30022, "商品已被他人修改，请刷新后重试"),

    CART_ITEM_NOT_FOUND(30023, "购物车项不存在"),
    CART_ITEM_PERMISSION_DENIED(30024, "无权操作该购物车项"),
    ORDER_NOT_FOUND(30025, "订单不存在"),
    ORDER_PERMISSION_DENIED(30026, "无权操作该订单"),
    ORDER_STATUS_INVALID(30027, "订单状态不允许该操作"),
    SUB_ORDER_NOT_FOUND(30028, "子订单不存在"),
    SUB_ORDER_STATUS_INVALID(30029, "子订单状态转换不合法"),
    PRODUCT_NOT_ON_SALE(30030, "商品不在销售中"),
    STOCK_NOT_ENOUGH(30031, "库存不足"),
    ORDER_IDEMPOTENT_CONFLICT(30032, "请勿重复提交订单"),
    PAYMENT_NOT_FOUND(30033, "支付记录不存在"),
    PAYMENT_STATUS_INVALID(30034, "支付状态异常"),
    REFUND_EXISTS(30035, "退款记录已存在"),
    REFUND_STATUS_INVALID(30036, "退款状态异常"),
    SUB_ORDER_PERMISSION_DENIED(30037, "无权操作该子订单"),
    REVIEW_EXISTS(30038, "该商品已评价，请勿重复评价"),
    REVIEW_NOT_FOUND(30039, "评价不存在"),
    REVIEW_PERMISSION_DENIED(30040, "无权操作该评价"),
    PRODUCT_NOT_IN_SUB_ORDER(30041, "商品不属于该子订单"),
    TRACE_NODE_NOT_FOUND(30042, "溯源节点不存在"),
    POST_NOT_FOUND(30043, "动态不存在"),
    POST_PERMISSION_DENIED(30044, "无权操作该动态"),
    COMMENT_NOT_FOUND(30045, "评论不存在"),

    PRESALE_NOT_FOUND(30046, "预售配置不存在"),
    PRESALE_ALREADY_EXISTS(30047, "该商品已设置预售"),
    PRESALE_STATUS_INVALID(30048, "预售状态不允许该操作"),
    PRESALE_PERMISSION_DENIED(30049, "无权操作该预售配置"),
    PRESALE_TIME_INVALID(30050, "预售时间设置不合法"),
    PRESALE_PRODUCT_NOT_READY(30051, "商品未通过审核或不具备开启预售条件"),
    PRESALE_RESERVATION_EXISTS(30052, "您已预约该预售，请勿重复预约"),
    PRESALE_RESERVATION_FULL(30053, "该预售预约名额已满"),

    SYSTEM_ERROR(99999, "系统内部错误");

    private final Integer code;
    private final String msg;
}
