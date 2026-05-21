package com.softpuzzle.pm.deliverable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 새 버전 생성 요청 (변경 요약 필수). */
public record NewVersionRequest(@NotBlank @Size(max = 2000) String changeSummary) {
}
