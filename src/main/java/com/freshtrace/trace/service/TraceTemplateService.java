package com.freshtrace.trace.service;

import com.freshtrace.trace.vo.TraceTemplateNodeVO;

import java.util.List;

/**
 * 溯源模板服务（Phase 5 Day 3）。
 * <p>
 * 模板为代码级预置配置，仅提供查询，供前端创建 TraceNode 时预填，不产生任何数据库写入。
 */
public interface TraceTemplateService {

    /**
     * 按品类查询溯源节点模板；未配置专属模板的品类返回通用默认模板。
     */
    List<TraceTemplateNodeVO> getTemplate(Long categoryId);
}
