package com.softpuzzle.pm.project;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * 서비스 레이어 인가 (defense in depth — 라우트 가시성과 별개).
 * admin = 전체 읽기, team/client = 활성 멤버인 프로젝트만.
 */
@Component
public class MembershipGuard {

    private final ProjectMemberMapper memberMapper;

    public MembershipGuard(ProjectMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    public boolean isAdmin(Account account) {
        return "admin".equals(account.getTier());
    }

    public boolean isTeam(Account account) {
        return "team".equals(account.getTier());
    }

    public boolean isMember(Long projectId, Account account) {
        return memberMapper.existsActive(projectId, account.getId());
    }

    /** 조회 권한: admin 전역 허용, 그 외 활성 멤버만. */
    public void assertCanView(Long projectId, Account account) {
        if (isAdmin(account)) {
            return;
        }
        if (!isMember(projectId, account)) {
            throw ApiException.forbidden("프로젝트 접근 권한이 없습니다.");
        }
    }

    /** 작업(쓰기) 권한: 활성 멤버만 (admin은 읽기 전용이라 쓰기 불가). */
    public void assertMember(Long projectId, Account account) {
        if (!isMember(projectId, account)) {
            throw ApiException.forbidden("프로젝트 멤버만 수행할 수 있습니다.");
        }
    }

    /** 프로젝트 생성 권한: 프로젝트팀 또는 관리자. */
    public void assertCanCreateProject(Account account) {
        if (!isTeam(account) && !isAdmin(account)) {
            throw ApiException.forbidden("프로젝트 생성 권한이 없습니다.");
        }
    }

    public boolean isClient(Account account) {
        return "client".equals(account.getTier());
    }

    /** 검토 요청·회수·파일 편집: 프로젝트팀 멤버만. */
    public void assertCanRequestReview(Long projectId, Account account) {
        if (!isTeam(account) || !isMember(projectId, account)) {
            throw ApiException.forbidden("프로젝트팀 멤버만 검토를 요청·회수할 수 있습니다.");
        }
    }

    /** 컨펌·반려: 고객사 멤버만. */
    public void assertCanReview(Long projectId, Account account) {
        if (!isClient(account) || !isMember(projectId, account)) {
            throw ApiException.forbidden("고객사 멤버만 컨펌·반려할 수 있습니다.");
        }
    }

    /** 프로젝트팀 작업(TC 관리·결함 처리 등): 프로젝트팀 멤버만. */
    public void assertTeamMember(Long projectId, Account account) {
        if (!isTeam(account) || !isMember(projectId, account)) {
            throw ApiException.forbidden("프로젝트팀 멤버만 수행할 수 있습니다.");
        }
    }
}
