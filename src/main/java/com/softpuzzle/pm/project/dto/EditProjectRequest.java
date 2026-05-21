package com.softpuzzle.pm.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EditProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "SaaS|웹사이트|모바일", message = "type은 SaaS/웹사이트/모바일 중 하나여야 합니다.") String type,
        @Size(max = 5000) String description,
        LocalDate startDate,
        LocalDate endDate) {
}
