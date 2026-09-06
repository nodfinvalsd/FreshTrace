package com.freshtrace.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.product.service.ProductService;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.trace.dto.TraceNodeCreateDTO;
import com.freshtrace.trace.dto.TraceNodeUpdateDTO;
import com.freshtrace.trace.entity.TraceNode;
import com.freshtrace.trace.enums.TraceNodeType;
import com.freshtrace.trace.mapper.TraceNodeMapper;
import com.freshtrace.trace.service.TraceNodeService;
import com.freshtrace.trace.vo.TraceNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 溯源节点实现（Phase 5 Day 2）。
 * <p>
 * 归属校验统一走 {@link #requireProductOwner}：跨模块通过 ProductService 查询商品，
 * 比较 product.farmerId 与当前 farmerId，不直接访问 ProductMapper。
 * 新增校验 productId 归属；修改/删除先从 TraceNode 反查 productId 再校验归属。
 * 删除复用 BaseEntity + @TableLogic 逻辑删除，不做物理 DELETE。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TraceNodeServiceImpl implements TraceNodeService {

    private final TraceNodeMapper traceNodeMapper;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TraceNodeVO createNode(Long farmerId, TraceNodeCreateDTO dto) {
        requireProductOwner(farmerId, dto.getProductId());
        requireValidNodeType(dto.getNodeType());

        TraceNode node = new TraceNode();
        node.setProductId(dto.getProductId());
        node.setNodeType(dto.getNodeType());
        node.setTitle(dto.getTitle());
        node.setDescription(dto.getDescription());
        node.setImages(toJson(dto.getImages()));
        node.setOccurredAt(dto.getOccurredAt());
        traceNodeMapper.insert(node);
        return toVO(node);
    }

    @Override
    @Transactional
    public TraceNodeVO updateNode(Long farmerId, Long nodeId, TraceNodeUpdateDTO dto) {
        TraceNode node = requireNode(nodeId);
        requireProductOwner(farmerId, node.getProductId());
        requireValidNodeType(dto.getNodeType());

        node.setNodeType(dto.getNodeType());
        node.setTitle(dto.getTitle());
        node.setDescription(dto.getDescription());
        node.setImages(toJson(dto.getImages()));
        node.setOccurredAt(dto.getOccurredAt());
        traceNodeMapper.updateById(node);
        return toVO(node);
    }

    @Override
    @Transactional
    public void deleteNode(Long farmerId, Long nodeId) {
        TraceNode node = requireNode(nodeId);
        requireProductOwner(farmerId, node.getProductId());
        traceNodeMapper.deleteById(nodeId);
    }

    @Override
    public List<TraceNodeVO> getTimeline(Long productId) {
        List<TraceNode> nodes = traceNodeMapper.selectList(new LambdaQueryWrapper<TraceNode>()
                .eq(TraceNode::getProductId, productId)
                .orderByAsc(TraceNode::getOccurredAt)
                .orderByAsc(TraceNode::getId));
        return nodes.stream().map(this::toVO).toList();
    }

    private TraceNode requireNode(Long nodeId) {
        TraceNode node = traceNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BizException(ErrorCode.TRACE_NODE_NOT_FOUND);
        }
        return node;
    }

    private void requireProductOwner(Long farmerId, Long productId) {
        ProductDetailVO detail = productService.detail(productId);
        if (!detail.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.PRODUCT_PERMISSION_DENIED);
        }
    }

    private void requireValidNodeType(Integer nodeType) {
        if (TraceNodeType.fromCode(nodeType) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点类型非法");
        }
    }

    private TraceNodeVO toVO(TraceNode node) {
        TraceNodeVO vo = new TraceNodeVO();
        vo.setId(node.getId());
        vo.setProductId(node.getProductId());
        vo.setNodeType(node.getNodeType());
        vo.setTitle(node.getTitle());
        vo.setDescription(node.getDescription());
        vo.setImages(parseImages(node.getImages()));
        vo.setOccurredAt(node.getOccurredAt());
        return vo;
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BIZ_ERROR, "溯源图片序列化失败");
        }
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("trace node images parse failed", e);
            return List.of();
        }
    }
}
