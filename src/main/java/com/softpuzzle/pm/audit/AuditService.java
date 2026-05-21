package com.softpuzzle.pm.audit;

import com.softpuzzle.pm.account.Account;
import java.util.List;
import org.springframework.stereotype.Service;

/** 감사 로그 기록·조회. 행위 시점 tier 스냅샷 보존. */
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 이벤트 트랜잭션 내에서 호출 (롤백 시 함께 취소). */
    public void log(Account actor, String action, String target) {
        AuditLog l = new AuditLog();
        l.setActorId(actor != null ? actor.getId() : null);
        l.setActorRole(actor != null ? actor.getTier() : "system");
        l.setAction(action);
        l.setTarget(target);
        auditLogMapper.insert(l);
    }

    public List<AuditLog> recent(int limit) {
        return auditLogMapper.findRecent(limit);
    }
}
