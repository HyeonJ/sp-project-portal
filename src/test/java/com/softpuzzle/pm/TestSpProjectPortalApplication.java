package com.softpuzzle.pm;

import org.springframework.boot.SpringApplication;

/** 로컬 실행용: ./gradlew bootTestRun — Testcontainers PostgreSQL로 앱 기동. */
public class TestSpProjectPortalApplication {

    public static void main(String[] args) {
        SpringApplication.from(SpProjectPortalApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
