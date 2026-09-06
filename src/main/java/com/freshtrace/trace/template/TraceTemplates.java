package com.freshtrace.trace.template;

import com.freshtrace.trace.enums.TraceNodeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 品类溯源模板（代码级预置配置，不落库）。
 * <p>
 * 模板是「建议发生哪些事件」，TraceNode 是「真实发生过哪些事件」；
 * 前端拉取模板仅用于预填，农户确认后仍经 {@code POST /api/trace/nodes} 写入 t_trace_node。
 * <p>
 * categoryId 为雪花运行时值，设计文档未给出真实品类映射，故 V1 仅提供通用默认模板兜底，
 * {@link #CATEGORY_TEMPLATES} 预留给后续按品类定制，新增品类只需在此补充映射。
 */
public final class TraceTemplates {

    private TraceTemplates() {
    }

    /**
     * 通用水果种植模板（对应设计文档「芒果」7 步，与 TraceNodeType 枚举一一对应，顺序稳定）。
     */
    private static final List<TemplateNode> DEFAULT_TEMPLATE = List.of(
            node(TraceNodeType.SOWING, "记录播种时间与方式"),
            node(TraceNodeType.FERTILIZING, "记录施肥时间与肥料种类"),
            node(TraceNodeType.FLOWERING, "记录开花时间与状态"),
            node(TraceNodeType.BAGGING, "记录套袋时间与方式"),
            node(TraceNodeType.RIPENING, "记录果实成熟情况"),
            node(TraceNodeType.HARVESTING, "记录采摘时间与方式"),
            node(TraceNodeType.SHIPPING, "记录发货时间与物流")
    );

    /**
     * 品类 -> 专属模板（当前为空，使用 {@link LinkedHashMap} 保证未来扩展时遍历顺序稳定）。
     */
    private static final Map<Long, List<TemplateNode>> CATEGORY_TEMPLATES = new LinkedHashMap<>();

    /**
     * 按品类取模板；未配置专属模板的品类回退到通用默认模板。
     */
    public static List<TemplateNode> get(Long categoryId) {
        return CATEGORY_TEMPLATES.getOrDefault(categoryId, DEFAULT_TEMPLATE);
    }

    private static TemplateNode node(TraceNodeType type, String description) {
        return new TemplateNode(type.getCode(), type.getDesc(), description);
    }
}
