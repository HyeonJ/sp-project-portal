package com.softpuzzle.pm.dev;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.audit.AuditService;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectGate;
import com.softpuzzle.pm.project.ProjectGateMapper;
import com.softpuzzle.pm.project.ProjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개발(코드 자동 생성) 트리거. 하드 사전조건: 게이트 9·11·13·15 전부 통과(REQ-DEV-001). */
@Service
public class DevRunService {

    private static final Logger log = LoggerFactory.getLogger(DevRunService.class);
    private static final short[] REQUIRED_GATES = {9, 11, 13, 15};
    private static final Map<Short, String> GATE_LABEL = Map.of(
            (short) 9, "요구사항", (short) 11, "IA", (short) 13, "디자인 시안", (short) 15, "프로토타입");

    private final DevRunMapper devRunMapper;
    private final ProjectGateMapper gateMapper;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;
    private final MembershipGuard guard;

    public DevRunService(DevRunMapper devRunMapper, ProjectGateMapper gateMapper,
                         ProjectMapper projectMapper, AuditService auditService, MembershipGuard guard) {
        this.devRunMapper = devRunMapper;
        this.gateMapper = gateMapper;
        this.projectMapper = projectMapper;
        this.auditService = auditService;
        this.guard = guard;
    }

    @Transactional
    public DevRun trigger(Long projectId, Account actor) {
        requireProject(projectId);
        guard.assertCanRequestReview(projectId, actor); // 프로젝트팀만 트리거

        List<String> unmet = unmetGates(projectId);
        if (!unmet.isEmpty()) {
            throw ApiException.conflict("DEV_PRECONDITION",
                    "개발 시작 사전조건 미충족 — 미통과 게이트: " + String.join(", ", unmet));
        }

        DevRun run = new DevRun();
        run.setProjectId(projectId);
        run.setResult("success");
        run.setTriggeredBy(actor.getId());
        devRunMapper.insert(run);
        projectMapper.bumpStage(projectId, 18);
        ProjectGate uat = gateMapper.findByProjectAndStageForUpdate(projectId, (short) 22);
        if (uat != null && "lock".equals(uat.getStatus())) {
            gateMapper.unlockToWait(uat.getId()); // 개발 완료 → UAT 게이트 해제(lock→wait)
        }
        auditService.log(actor, "TRIGGER_DEV", "project=" + projectId);
        log.info("[trigger] project={} by={} → dev_run={}", projectId, actor.getEmail(), run.getId());
        return run;
    }

    @Transactional(readOnly = true)
    public List<DevRun> history(Long projectId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        return devRunMapper.findByProject(projectId);
    }

    private List<String> unmetGates(Long projectId) {
        Map<Short, String> byStage = gateMapper.findByProject(projectId).stream()
                .collect(Collectors.toMap(ProjectGate::getGateStage, ProjectGate::getStatus));
        return java.util.Arrays.stream(toBoxed(REQUIRED_GATES))
                .filter(stage -> !"pass".equals(byStage.get(stage)))
                .map(stage -> "Gate " + stage + " " + GATE_LABEL.get(stage))
                .collect(Collectors.toList());
    }

    private Short[] toBoxed(short[] arr) {
        Short[] out = new Short[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[i];
        }
        return out;
    }

    private void requireProject(Long projectId) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
    }
}
