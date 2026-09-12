package com.freshtrace.community.vo;

import com.freshtrace.product.vo.ProductVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 果农主页聚合 VO（Phase 6 Day 4）。
 * <p>
 * 聚合果园信息 + 平均评分/评价数 + 在售商品 + 历史动态预览，整体缓存于 farmer:home:{farmerId}。
 * 因整体缓存，posts 不含个性化点赞态（由 PostService 以匿名身份查询，liked 恒 false），
 * 用户查看点赞状态请进入动态详情。
 */
@Data
public class FarmerHomeVO {

    private Long farmerId;

    private String realName;

    private String orchardName;

    private String orchardProvince;

    private String orchardCity;

    private String orchardDistrict;

    private String orchardAddress;

    private BigDecimal orchardArea;

    private List<String> orchardPhotos;

    private BigDecimal avgRating;

    private Integer totalSales;

    /** 该果农累计有效评价数 */
    private Integer reviewCount;

    /** 在售商品（lifecycle=销售中 且 审核通过），按销量倒序 */
    private List<ProductVO> products;

    /** 历史动态预览（最近若干条） */
    private List<PostVO> posts;
}
