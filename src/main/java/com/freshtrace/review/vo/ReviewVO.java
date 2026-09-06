package com.freshtrace.review.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价展示 VO。仅包含评价展示所需信息，不携带买家手机号/地址等敏感数据。
 */
@Data
public class ReviewVO {

    private Long reviewId;

    private Long productId;

    private Long farmerId;

    private Integer rating;

    private String content;

    private List<String> images;

    private String reply;

    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;
}
