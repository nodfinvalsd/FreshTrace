package com.freshtrace.admin.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端操作日志注解。
 * <p>
 * 标注在管理接口方法上，由 {@link OperationLogAspect} 在方法成功执行后记录
 * 操作人 / 对象类型 / 对象ID / 动作 / IP / 参数摘要，用于管理员操作审计与状态变更追溯。
 * 仅记录成功操作：业务校验失败或抛异常时不会产生「成功」日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作动作，如 "审核果农认证" */
    String action();

    /** 操作对象类型，如 FARMER / PRODUCT / REFUND / REPORT */
    String targetType();

    /** 目标ID所在方法参数下标（从0开始），默认取第一个参数 */
    int targetIdArgIndex() default 0;

    /**
     * 当目标参数为对象时，取其属性名作为目标ID（支持点路径，如 "farmerId"）；
     * 为空表示参数本身即ID（Long）。
     */
    String targetIdProperty() default "";
}
