package com.freshtrace.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileDTO {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Size(max = 500, message = "头像地址长度不能超过 500")
    private String avatarUrl;
}
