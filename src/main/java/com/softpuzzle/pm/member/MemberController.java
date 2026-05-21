package com.softpuzzle.pm.member;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.member.dto.MemberRequests;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class MemberController {

    private final MemberService memberService;
    private final CurrentUser currentUser;

    public MemberController(MemberService memberService, CurrentUser currentUser) {
        this.memberService = memberService;
        this.currentUser = currentUser;
    }

    @PostMapping("/invite")
    public ApiResponse<MemberService.InviteResult> invite(@PathVariable Long projectId,
                                                          @Valid @RequestBody MemberRequests.Invite req) {
        return ApiResponse.ok(memberService.invite(projectId, req.email(), req.inviteType(), currentUser.require()));
    }

    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> remove(@PathVariable Long projectId, @PathVariable Long accountId) {
        memberService.remove(projectId, accountId, currentUser.require());
        return ApiResponse.ok(null);
    }
}
