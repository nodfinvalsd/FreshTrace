package com.freshtrace.trace.service;

import com.freshtrace.trace.dto.TraceNodeCreateDTO;
import com.freshtrace.trace.dto.TraceNodeUpdateDTO;
import com.freshtrace.trace.vo.TraceNodeVO;

import java.util.List;

/**
 * 溯源节点服务（Phase 5 Day 2）。
 * <p>
 * 权限模型：Farmer 身份 + 目标商品属于当前 Farmer = 允许操作。
 * 新增/修改/删除均需先校验商品归属，防止果农给他人商品录入溯源记录。
 */
public interface TraceNodeService {

    /**
     * 果农新增溯源节点（校验 productId 归属后保存）。
     */
    TraceNodeVO createNode(Long farmerId, TraceNodeCreateDTO dto);

    /**
     * 果农修改溯源节点（反查节点所属商品后校验归属）。
     */
    TraceNodeVO updateNode(Long farmerId, Long nodeId, TraceNodeUpdateDTO dto);

    /**
     * 果农删除溯源节点（反查节点所属商品后校验归属，逻辑删除）。
     */
    void deleteNode(Long farmerId, Long nodeId);

    /**
     * 公开查询商品溯源时间线，按 occurred_at ASC、id ASC 稳定排序。
     */
    List<TraceNodeVO> getTimeline(Long productId);
}
