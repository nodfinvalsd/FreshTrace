package com.freshtrace.trace.service.impl;

import com.freshtrace.trace.service.TraceTemplateService;
import com.freshtrace.trace.template.TemplateNode;
import com.freshtrace.trace.template.TraceTemplates;
import com.freshtrace.trace.vo.TraceTemplateNodeVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 溯源模板实现（Phase 5 Day 3）。
 * <p>
 * 纯静态配置转换，无 Mapper、无事务、无缓存；模板不写入 t_trace_node。
 */
@Service
public class TraceTemplateServiceImpl implements TraceTemplateService {

    @Override
    public List<TraceTemplateNodeVO> getTemplate(Long categoryId) {
        return TraceTemplates.get(categoryId).stream().map(this::toVO).toList();
    }

    private TraceTemplateNodeVO toVO(TemplateNode node) {
        TraceTemplateNodeVO vo = new TraceTemplateNodeVO();
        vo.setNodeType(node.nodeType());
        vo.setTitle(node.title());
        vo.setDescription(node.description());
        return vo;
    }
}
