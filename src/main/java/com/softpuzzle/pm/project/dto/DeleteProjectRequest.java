package com.softpuzzle.pm.project.dto;

import jakarta.validation.constraints.NotBlank;

/** 프로젝트 삭제 확인 — 이름 정확 입력. */
public record DeleteProjectRequest(@NotBlank String confirmName) {
}
