package com.softpuzzle.pm.notify;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.project.ProjectMember;
import com.softpuzzle.pm.project.ProjectMemberMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인앱 알림. 이벤트 트랜잭션 내에서 생성(롤백 시 함께 취소). */
@Service
public class NotificationService {

    private static final int LIST_LIMIT = 30;

    private final NotificationMapper notificationMapper;
    private final ProjectMemberMapper memberMapper;

    public NotificationService(NotificationMapper notificationMapper, ProjectMemberMapper memberMapper) {
        this.notificationMapper = notificationMapper;
        this.memberMapper = memberMapper;
    }

    @Transactional(readOnly = true)
    public List<Notification> list(Account actor) {
        return notificationMapper.findByRecipient(actor.getId(), LIST_LIMIT);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Account actor) {
        return notificationMapper.unreadCount(actor.getId());
    }

    @Transactional
    public void markRead(Long id, Account actor) {
        notificationMapper.markRead(id, actor.getId());
    }

    @Transactional
    public void markAllRead(Account actor) {
        notificationMapper.markAllRead(actor.getId());
    }

    public void notify(Long recipientId, String type, String body, String link) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setBody(body);
        n.setLink(link);
        notificationMapper.insert(n);
    }

    /** 프로젝트의 특정 tier 활성 멤버에게 알림 (행위자 제외). tier=null이면 전원. */
    public void notifyProjectTier(Long projectId, String tier, Long excludeAccountId,
                                  String type, String body, String link) {
        for (ProjectMember m : memberMapper.findActiveMembers(projectId)) {
            if (m.getAccountId().equals(excludeAccountId)) {
                continue;
            }
            if (tier != null && !tier.equals(m.getAccountTier())) {
                continue;
            }
            notify(m.getAccountId(), type, body, link);
        }
    }
}
