package com.softpuzzle.pm.search;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.TestCase;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 통합 검색 — 접근 가능한 프로젝트 범위 내 프로젝트·TC·결함. */
@Service
public class SearchService {

    public record SearchResults(List<Project> projects, List<TestCase> testCases, List<Defect> defects) {
    }

    private final ProjectMapper projectMapper;
    private final SearchMapper searchMapper;
    private final MembershipGuard guard;

    public SearchService(ProjectMapper projectMapper, SearchMapper searchMapper, MembershipGuard guard) {
        this.projectMapper = projectMapper;
        this.searchMapper = searchMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public SearchResults search(String query, Account actor) {
        if (query == null || query.isBlank()) {
            return new SearchResults(List.of(), List.of(), List.of());
        }
        String q = query.trim();
        String ql = q.toLowerCase();
        List<Project> accessible = guard.isAdmin(actor)
                ? projectMapper.findAll() : projectMapper.findActiveByMember(actor.getId());
        List<Long> ids = accessible.stream().map(Project::getId).toList();
        List<Project> projects = accessible.stream()
                .filter(p -> p.getName().toLowerCase().contains(ql))
                .limit(15).toList();
        List<TestCase> tcs = ids.isEmpty() ? List.of() : searchMapper.searchTestCases(ids, q);
        List<Defect> defects = ids.isEmpty() ? List.of() : searchMapper.searchDefects(ids, q);
        return new SearchResults(projects, tcs, defects);
    }
}
