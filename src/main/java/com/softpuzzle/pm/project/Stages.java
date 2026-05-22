package com.softpuzzle.pm.project;

/** 20단계 라벨 조회 (대시보드·로드맵 공용). flow-diagram.md 기준. */
public final class Stages {

    private Stages() {
    }

    public static final int TOTAL = 20;

    private static final String[] NAMES = {
            "",
            "프로젝트 생성·워크스페이스 초기화", "요구사항 등록", "고객사 검토",
            "프로젝트팀 수정·반영", "고객사 최종 컨펌", "IA 등록",
            "고객사 IA 컨펌", "디자인 시안 등록", "고객사 시안 컨펌",
            "프로토타입(HTML) 등록", "고객사 프로토타입 컨펌", "Figma 핸드오프 등록",
            "고객사 Figma 컨펌", "개발", "테스트 항목 관리",
            "고객사 UAT", "프로젝트팀 수정·재배포", "최종 검수 컨펌",
            "산출물 Export", "서비스 오픈·운영 전환"
    };

    public static String name(int stage) {
        if (stage < 1 || stage > TOTAL) {
            return "";
        }
        return NAMES[stage];
    }

    public static String label(int stage) {
        return stage + ". " + name(stage);
    }
}
