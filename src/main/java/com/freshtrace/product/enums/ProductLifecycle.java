package com.freshtrace.product.enums;

import lombok.Getter;

@Getter
public enum ProductLifecycle {

    PLANTING(0, "种植中"),
    PRESALE(1, "预售中"),
    RIPE(2, "成熟待采摘"),
    ON_SALE(3, "销售中"),
    SOLD_OUT(4, "售罄");

    private final int code;
    private final String desc;

    ProductLifecycle(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 主生命周期仅允许单向推进，不允许回退。
     * 预售取消（PRESALE -> PLANTING）与补货（SOLD_OUT -> ON_SALE）通过独立业务动作完成，不走此白名单。
     */
    public boolean canTransitionTo(ProductLifecycle target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PLANTING -> target == PRESALE;
            case PRESALE -> target == RIPE;
            case RIPE -> target == ON_SALE;
            case ON_SALE -> target == SOLD_OUT;
            case SOLD_OUT -> false;
        };
    }

    public static ProductLifecycle fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductLifecycle lifecycle : values()) {
            if (lifecycle.code == code) {
                return lifecycle;
            }
        }
        return null;
    }
}
