package com.softpuzzle.pm.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AdminRequests {

    public record CreateAccount(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "team|client|admin") String tier,
            String job,
            @Size(max = 100) String clientOrgName) {
    }

    public record StatusChange(@Pattern(regexp = "active|inactive|pending") String status) {
    }

    private AdminRequests() {
    }
}
