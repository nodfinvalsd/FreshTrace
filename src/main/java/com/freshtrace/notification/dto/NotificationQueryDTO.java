package com.freshtrace.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通知列表分页参数。
 * <p>
 * type / read 为可选过滤：type 对应 {@code NotificationType}.code，read=true 只看已读、false 只看未读。
 */
@Data
public class NotificationQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer size = 10;

    /** 通知类型过滤，可空 */
    private Integer type;

    /** 已读状态过滤，可空 */
    private Boolean read;
}
