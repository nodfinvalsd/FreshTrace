package com.freshtrace.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshtrace.common.BizException;
import com.freshtrace.common.ErrorCode;
import com.freshtrace.common.PageVO;
import com.freshtrace.common.cache.CacheKeys;
import com.freshtrace.farmer.entity.Farmer;
import com.freshtrace.farmer.mapper.FarmerMapper;
import com.freshtrace.review.dto.ReviewCreateDTO;
import com.freshtrace.review.dto.ReviewQueryDTO;
import com.freshtrace.review.dto.ReviewReplyDTO;
import com.freshtrace.review.entity.Review;
import com.freshtrace.review.mapper.ReviewMapper;
import com.freshtrace.review.service.ReviewService;
import com.freshtrace.review.vo.ReviewVO;
import com.freshtrace.trade.entity.Order;
import com.freshtrace.trade.entity.OrderItem;
import com.freshtrace.trade.entity.SubOrder;
import com.freshtrace.trade.enums.SubOrderStatus;
import com.freshtrace.trade.mapper.OrderItemMapper;
import com.freshtrace.trade.mapper.OrderMapper;
import com.freshtrace.trade.mapper.SubOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价实现（Phase 4 Day 3）。
 * <p>
 * 事务与并发设计：
 * - 创建评价：事务内先锁果农行（FOR UPDATE）→ INSERT review → 实时 AVG → UPDATE avg_rating。
 *   行锁串行化同一果农的并发评价，保证「先插入者的评价被后提交者 AVG 看到」，
 *   最终 avg_rating 与数据库实际 AVG 一致（V1 实时 AVG，不做异步聚合）；
 * - 重复评价：业务预查给出友好异常，UNIQUE(sub_order_id, product_id) 兜底，
 *   唯一键冲突在事务内捕获并转换为明确业务异常；
 * - 缓存：事务提交后 invalidate farmer:home:{farmerId}，失败仅记日志不影响业务事实。
 */
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final SubOrderMapper subOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final FarmerMapper farmerMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ReviewServiceImpl(ReviewMapper reviewMapper,
                             SubOrderMapper subOrderMapper,
                             OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper,
                             FarmerMapper farmerMapper,
                             StringRedisTemplate stringRedisTemplate,
                             ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.reviewMapper = reviewMapper;
        this.subOrderMapper = subOrderMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.farmerMapper = farmerMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public ReviewVO createReview(Long userId, ReviewCreateDTO dto) {
        SubOrder subOrder = requireReviewableSubOrder(userId, dto.getSubOrderNo(), dto.getProductId());

        // 业务预查：友好异常提示（最终唯一性由 UNIQUE 约束兜底）
        if (reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getSubOrderId, subOrder.getId())
                .eq(Review::getProductId, dto.getProductId())) > 0) {
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }

        Review review = transactionTemplate.execute(status -> persistReview(subOrder, userId, dto));

        // 事务提交后失效果农主页缓存：派生数据删除失败不影响已提交评价
        try {
            stringRedisTemplate.delete(CacheKeys.farmerHome(subOrder.getFarmerId()));
        } catch (Exception e) {
            log.warn("invalidate farmer home cache failed, farmerId={}", subOrder.getFarmerId(), e);
        }
        return toVO(review);
    }

    @Override
    @Transactional
    public void reply(Long farmerId, Long reviewId, ReviewReplyDTO dto) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getFarmerId().equals(farmerId)) {
            throw new BizException(ErrorCode.REVIEW_PERMISSION_DENIED);
        }
        // V1：单字段覆盖式回复（PRD 未定义回复状态机/仅一次限制），不触发评分重算
        Review update = new Review();
        update.setId(reviewId);
        update.setReply(dto.getReply());
        update.setRepliedAt(LocalDateTime.now().withNano(0));
        reviewMapper.updateById(update);
    }

    @Override
    public PageVO<ReviewVO> page(ReviewQueryDTO query) {
        if (query.getProductId() == null && query.getFarmerId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "必须指定商品或果农");
        }
        // 逻辑删除由 MyBatis-Plus 自动过滤（deleted=0），不返回已删评价
        Page<Review> page = new Page<>(query.getPage(), query.getSize());
        reviewMapper.selectPage(page, new LambdaQueryWrapper<Review>()
                .eq(query.getProductId() != null, Review::getProductId, query.getProductId())
                .eq(query.getFarmerId() != null, Review::getFarmerId, query.getFarmerId())
                .orderByDesc(Review::getId));
        List<ReviewVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageVO.of(page, records);
    }

    /**
     * 事务外校验链：子订单存在 → 买家归属 → 已完成 → 商品属于该子订单。
     */
    private SubOrder requireReviewableSubOrder(Long userId, String subOrderNo, Long productId) {
        SubOrder subOrder = subOrderMapper.selectOne(new LambdaQueryWrapper<SubOrder>()
                .eq(SubOrder::getSubOrderNo, subOrderNo));
        if (subOrder == null) {
            throw new BizException(ErrorCode.SUB_ORDER_NOT_FOUND);
        }
        Order order = orderMapper.selectById(subOrder.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_PERMISSION_DENIED);
        }
        if (SubOrderStatus.FINISHED.getCode() != subOrder.getStatus()) {
            throw new BizException(ErrorCode.SUB_ORDER_STATUS_INVALID, "子订单未完成，不能评价");
        }
        if (orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getSubOrderId, subOrder.getId())
                .eq(OrderItem::getProductId, productId)) == 0) {
            throw new BizException(ErrorCode.PRODUCT_NOT_IN_SUB_ORDER);
        }
        return subOrder;
    }

    /**
     * 事务内：锁果农行 → INSERT review（UNIQUE 兜底）→ 实时 AVG → UPDATE avg_rating。
     */
    private Review persistReview(SubOrder subOrder, Long userId, ReviewCreateDTO dto) {
        // 锁果农行：串行化同一果农的并发评价，保证 avg_rating 聚合正确性
        Long lockedFarmerId = farmerMapper.selectIdForUpdate(subOrder.getFarmerId());
        if (lockedFarmerId == null) {
            throw new BizException(ErrorCode.FARMER_NOT_EXISTS);
        }

        Review review = new Review();
        review.setSubOrderId(subOrder.getId());
        review.setProductId(dto.getProductId());
        review.setUserId(userId);
        review.setFarmerId(subOrder.getFarmerId());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setImages(toJson(dto.getImages()));
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发重复评价：UNIQUE(sub_order_id, product_id) 最终兜底
            throw new BizException(ErrorCode.REVIEW_EXISTS);
        }

        BigDecimal avg = reviewMapper.selectAvgRating(subOrder.getFarmerId());
        if (avg != null) {
            // 有有效评价才更新；无评价时保持 t_farmer.avg_rating 默认值（5.0）语义
            Farmer update = new Farmer();
            update.setId(subOrder.getFarmerId());
            update.setAvgRating(avg);
            farmerMapper.updateById(update);
        }
        return review;
    }

    private ReviewVO toVO(Review review) {
        ReviewVO vo = new ReviewVO();
        vo.setReviewId(review.getId());
        vo.setProductId(review.getProductId());
        vo.setFarmerId(review.getFarmerId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setImages(parseImages(review.getImages()));
        vo.setReply(review.getReply());
        vo.setRepliedAt(review.getRepliedAt());
        vo.setCreatedAt(review.getCreateTime());
        return vo;
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new BizException(ErrorCode.BIZ_ERROR, "评价图片序列化失败");
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
            log.warn("review images parse failed", e);
            return List.of();
        }
    }
}
