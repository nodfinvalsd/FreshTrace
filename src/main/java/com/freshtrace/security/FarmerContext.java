package com.freshtrace.security;

/**
 * 果农上下文：保存当前请求对应的 farmerId（t_farmer.id）。
 * <p>
 * 由 {@link FarmerAspect} 在 {@code @FarmerRequired} 校验通过后写入，
 * 请求结束（正常/异常返回）由切面 {@code @After} 统一清理，避免线程复用导致 ThreadLocal 泄漏。
 * 风格与 {@link UserContext} 保持一致。
 */
public class FarmerContext {

    private static final ThreadLocal<Long> FARMER_ID = new ThreadLocal<>();

    private FarmerContext() {
    }

    public static void set(Long farmerId) {
        FARMER_ID.set(farmerId);
    }

    public static Long get() {
        return FARMER_ID.get();
    }

    public static void clear() {
        FARMER_ID.remove();
    }
}
