package com.freshtrace.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshtrace.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper（基础 CRUD）。
 * <p>
 * 列表、未读数、已读全部由 MyBatis-Plus 条件构造器完成，无需自定义 SQL。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
