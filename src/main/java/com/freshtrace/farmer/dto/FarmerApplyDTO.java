package com.freshtrace.farmer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FarmerApplyDTO {

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    @NotBlank(message = "果园名称不能为空")
    private String orchardName;

    @NotBlank(message = "果园所在省不能为空")
    private String orchardProvince;

    @NotBlank(message = "果园所在市不能为空")
    private String orchardCity;

    @NotBlank(message = "果园所在区不能为空")
    private String orchardDistrict;

    @NotBlank(message = "果园详细地址不能为空")
    private String orchardAddress;

    private BigDecimal orchardArea;

    private List<String> categoryIds;

    private List<String> orchardPhotos;

    private String certificationImage;
}
