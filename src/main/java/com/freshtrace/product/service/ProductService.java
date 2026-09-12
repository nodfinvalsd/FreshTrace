package com.freshtrace.product.service;

import com.freshtrace.product.dto.ProductAuditDTO;
import com.freshtrace.product.dto.ProductCreateDTO;
import com.freshtrace.product.dto.ProductLifecycleUpdateDTO;
import com.freshtrace.product.dto.ProductUpdateDTO;
import com.freshtrace.product.vo.ProductDetailVO;
import com.freshtrace.product.vo.ProductVO;

import java.util.Collection;
import java.util.List;

public interface ProductService {

    ProductVO create(Long userId, ProductCreateDTO dto);

    ProductDetailVO detail(Long id);

    /**
     * 批量查询商品基础信息（不含属性/图片），用于列表场景避免 N+1。
     * 返回顺序与入参无关，调用方按 id 建索引。
     */
    List<ProductVO> batchBrief(Collection<Long> productIds);

    ProductVO update(Long userId, Long id, ProductUpdateDTO dto);

    void audit(Long id, ProductAuditDTO dto);

    ProductVO updateLifecycle(Long userId, Long id, ProductLifecycleUpdateDTO dto);

    ProductVO cancelPreSale(Long userId, Long id);

    ProductVO restock(Long userId, Long id);

    /**
     * 系统级：将商品推进到「预售中」。幂等：已为 PRESALE 直接返回；
     * 仅允许满足状态机白名单的前置状态（如 PLANTING）转入，由预售模块在归属/审核校验通过后调用。
     */
    void markPresale(Long productId);

    /**
     * 系统级：关闭预售时回退商品到「种植中」。幂等：商品已不在 PRESALE 直接返回。
     */
    void markPresaleCancelled(Long productId);

    /**
     * 系统级：预售到期后将商品转为「销售中」。按状态机合法链路 PRESALE → RIPE → ON_SALE 推进，
     * 幂等：已为 ON_SALE 直接返回；RIPE 可一步转入 ON_SALE。
     */
    void markPresaleEnded(Long productId);
}
