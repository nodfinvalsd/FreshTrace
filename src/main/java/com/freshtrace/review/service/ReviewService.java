package com.freshtrace.review.service;

import com.freshtrace.common.PageVO;
import com.freshtrace.review.dto.ReviewCreateDTO;
import com.freshtrace.review.dto.ReviewQueryDTO;
import com.freshtrace.review.dto.ReviewReplyDTO;
import com.freshtrace.review.vo.ReviewVO;

/**
 * 评价服务（Phase 4 Day 3）。
 * <p>
 * 评价数据真实性四条规则（缺一不可）：
 * 1. 仅 FINISHED 子订单可评价；
 * 2. 评价人必须是该订单买家（服务端上下文，不信任客户端参数）；
 * 3. 被评价商品必须属于该子订单（t_order_item 事实校验）；
 * 4. 同一子订单同一商品只能评价一次（业务预查 + UNIQUE 约束兜底）。
 */
public interface ReviewService {

    /**
     * 买家发表评价：创建 Review + 实时重算 Farmer.avgRating（同一事务），
     * 事务提交后失效果农主页缓存。
     */
    ReviewVO createReview(Long userId, ReviewCreateDTO dto);

    /**
     * 果农回复评价：仅该评价对应果农可回复，覆盖式更新 reply/repliedAt，不触发评分重算。
     */
    void reply(Long farmerId, Long reviewId, ReviewReplyDTO dto);

    /**
     * 评价列表：productId / farmerId 至少传其一，都传时按 AND 过滤，时间倒序分页。
     */
    PageVO<ReviewVO> page(ReviewQueryDTO query);
}
