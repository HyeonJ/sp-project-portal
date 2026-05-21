package com.softpuzzle.pm.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 프로젝트 생성 요청. 고객사는 회사명으로 find-or-create. */
public record CreateProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String clientOrgName,
        @Pattern(regexp = "SaaS|웹사이트|모바일", message = "type은 SaaS/웹사이트/모바일 중 하나여야 합니다.")
        String type,
        @Size(max = 5000) String description,
        LocalDate startDate,
        LocalDate endDate) {
}
