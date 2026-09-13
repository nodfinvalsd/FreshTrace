package com.freshtrace.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户提交举报参数。
 */
@Data
public class ReportSubmitDTO {

    /** 1=溯源信息,2=商品品质,3=果农行为 */
    @NotNull(message = "举报对象类型不能为空")
    private Integer targetType;

    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 500, message = "举报原因过长")
    private String reason;

    @Size(max = 9, message = "凭证图片最多9张")
    private List<String> evidenceImages;
}
