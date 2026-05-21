package com.softpuzzle.pm.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MemberRequests {

    public record Invite(@NotBlank @Email String email,
                         @Pattern(regexp = "client|team_member") String inviteType) {
    }

    public record Accept(@NotBlank String token, @NotBlank @Size(max = 100) String password) {
    }

    public record ResetRequest(@NotBlank @Email String email) {
    }

    public record ResetConfirm(@NotBlank String token, @NotBlank @Size(max = 100) String password) {
    }

    private MemberRequests() {
    }
}
