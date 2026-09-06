package com.freshtrace.trace.template;

/**
 * 溯源模板节点（静态配置对象，非数据库实体）。
 * <p>
 * 表达「该品类建议有哪些溯源事件」，仅用于前端创建 TraceNode 时预填，
 * 不携带 id/productId/occurredAt 等真实溯源记录字段。
 */
public record TemplateNode(int nodeType, String title, String description) {
}
