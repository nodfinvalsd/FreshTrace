package com.freshtrace.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshVO {

    private String accessToken;

    private long accessExpire;
}
