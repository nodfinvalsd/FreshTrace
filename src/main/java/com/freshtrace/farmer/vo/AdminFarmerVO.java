package com.freshtrace.farmer.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端果农认证视图。
 * <p>
 * 列表接口不返回身份证号（脱敏）；详情接口解密后返回，供管理员审核核验。
 */
@Data
public class AdminFarmerVO {

    private Long id;

    private Long userId;

    private String username;

    private String phone;

    private String realName;

    /** 身份证号：列表为 null，详情为解密明文 */
    private String idCard;

    private String orchardName;

    private String orchardProvince;

    private String orchardCity;

    private String orchardDistrict;

    private String orchardAddress;

    private BigDecimal orchardArea;

    private List<String> categoryIds;

    private List<String> orchardPhotos;

    private String certificationImage;

    private Integer auditStatus;

    private String auditReason;

    private LocalDateTime auditedAt;

    private Long auditedBy;

    private LocalDateTime createTime;
}
