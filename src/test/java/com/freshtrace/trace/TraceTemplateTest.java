package com.freshtrace.trace;

import com.freshtrace.trace.template.TemplateNode;
import com.freshtrace.trace.template.TraceTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 溯源模板单元测试（Phase 5 Day 4）。
 * 验证代码级模板配置：节点齐全、顺序稳定、未知品类回退默认模板、纯静态无副作用。
 */
class TraceTemplateTest {

    @Test
    void defaultTemplateContainsSevenNodesInOrder() {
        List<TemplateNode> nodes = TraceTemplates.get(1L);

        assertThat(nodes).hasSize(7);
        assertThat(nodes.stream().map(TemplateNode::title).toList())
                .containsExactly("播种", "施肥", "开花", "套袋", "成熟", "采摘", "发货");
    }

    @Test
    void nodeTypeCodesMatchEnum() {
        List<TemplateNode> nodes = TraceTemplates.get(1L);

        assertThat(nodes.stream().map(TemplateNode::nodeType).toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void everyNodeHasDescription() {
        List<TemplateNode> nodes = TraceTemplates.get(1L);

        assertThat(nodes).allSatisfy(node -> assertThat(node.description()).isNotBlank());
    }

    @Test
    void unknownCategoryFallsBackToDefaultTemplate() {
        assertThat(TraceTemplates.get(999999L)).hasSize(7);
    }

    @Test
    void repeatedCallsReturnStableOrder() {
        List<String> first = TraceTemplates.get(1L).stream().map(TemplateNode::title).toList();
        List<String> second = TraceTemplates.get(1L).stream().map(TemplateNode::title).toList();

        assertThat(first).isEqualTo(second);
    }
}
