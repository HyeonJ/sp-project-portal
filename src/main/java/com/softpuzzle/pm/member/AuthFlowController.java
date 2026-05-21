package com.softpuzzle.pm.member;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.member.dto.MemberRequests;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 미인증 사용자용 — 초대 수락·비밀번호 재설정 (permitAll). */
@RestController
public class AuthFlowController {

    private final MemberService memberService;
    private final PasswordResetService passwordResetService;

    public AuthFlowController(MemberService memberService, PasswordResetService passwordResetService) {
        this.memberService = memberService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/api/invite/accept")
    public ApiResponse<Void> accept(@Valid @RequestBody MemberRequests.Accept req) {
        memberService.accept(req.token(), req.password());
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/password/reset-request")
    public ApiResponse<Map<String, String>> resetRequest(@Valid @RequestBody MemberRequests.ResetRequest req) {
        String url = passwordResetService.requestReset(req.email());
        // 이메일 미발송 환경: 존재 시 링크 반환(데모). 계정 존재 여부는 항상 동일 메시지로 비노출.
        Map<String, String> data = url != null ? Map.of("resetUrl", url) : Map.of();
        return ApiResponse.ok(data);
    }

    @PostMapping("/api/password/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody MemberRequests.ResetConfirm req) {
        passwordResetService.reset(req.token(), req.password());
        return ApiResponse.ok(null);
    }
}
