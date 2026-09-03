package com.freshtrace.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.freshtrace.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String phone;

    private Integer role;

    private Integer status;
}
