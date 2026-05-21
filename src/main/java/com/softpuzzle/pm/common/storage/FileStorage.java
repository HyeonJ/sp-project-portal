package com.softpuzzle.pm.common.storage;

import java.io.InputStream;

/** 파일 스토리지 경계 (dev=local, prod=S3). 워크플로우 규칙은 갖지 않음. */
public interface FileStorage {

    /** 객체 저장 → 스토리지 키 반환. */
    String put(String keyHint, String contentType, long size, InputStream content);

    /** 기존 객체를 새 키로 복사 → 새 스토리지 키 반환 (새 버전 스냅샷용). */
    String copy(String sourceStorageKey);

    /** 단명 다운로드 URL (presigned). local은 임시 토큰 URL. */
    String presignedGetUrl(String storageKey, java.time.Duration ttl);

    /** best-effort 삭제 (orphan 보상). */
    void deleteQuietly(String storageKey);
}
