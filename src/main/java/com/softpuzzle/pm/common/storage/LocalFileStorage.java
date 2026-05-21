package com.softpuzzle.pm.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬 디렉토리 스토리지 (dev). 객체 키는 난수화, 로컬 경로를 비즈니스 데이터로 저장하지 않음. */
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path baseDir;

    public LocalFileStorage(@Value("${storage.local-dir:./.storage}") String localDir) {
        this.baseDir = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("스토리지 디렉토리 생성 실패: " + baseDir, e);
        }
    }

    @Override
    public String put(String keyHint, String contentType, long size, InputStream content) {
        String storageKey = UUID.randomUUID().toString().replace("-", "");
        Path target = baseDir.resolve(storageKey);
        try {
            Files.copy(content, target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + storageKey, e);
        }
        return storageKey;
    }

    @Override
    public String copy(String sourceStorageKey) {
        String newKey = UUID.randomUUID().toString().replace("-", "");
        try {
            Files.copy(baseDir.resolve(sourceStorageKey), baseDir.resolve(newKey));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 복사 실패: " + sourceStorageKey, e);
        }
        return newKey;
    }

    @Override
    public InputStream openStream(String storageKey) {
        try {
            return Files.newInputStream(baseDir.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 읽기 실패: " + storageKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String storageKey, Duration ttl) {
        // dev: 다운로드 컨트롤러 경유 (실서비스는 S3 presigned). 단순화한 토큰 URL.
        return "/api/files/local/" + storageKey;
    }

    @Override
    public void deleteQuietly(String storageKey) {
        try {
            Files.deleteIfExists(baseDir.resolve(storageKey));
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
