package com.softpuzzle.pm.deliverable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 반려 요청 (사유 필수). */
public record RejectRequest(@NotBlank @Size(max = 2000) String reason) {
}
