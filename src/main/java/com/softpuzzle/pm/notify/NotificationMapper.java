package com.softpuzzle.pm.notify;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    List<Notification> findByRecipient(@Param("recipientId") Long recipientId, @Param("limit") int limit);

    int unreadCount(@Param("recipientId") Long recipientId);

    void insert(Notification notification);

    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    int markAllRead(@Param("recipientId") Long recipientId);
}
