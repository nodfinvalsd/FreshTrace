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

    SYSTEM_ERROR(99999, "系统内部错误");

    private final Integer code;
    private final String msg;
}
