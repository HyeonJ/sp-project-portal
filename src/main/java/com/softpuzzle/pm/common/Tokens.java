package com.softpuzzle.pm.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 초대·비밀번호 재설정 단명 토큰: 원문은 링크에만, 저장은 SHA-256 해시(결정적 조회). */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** URL-safe 난수 토큰 원문 (32바이트). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 조회용 결정적 해시 (SHA-256 hex). */
    public static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("토큰 해시 실패", e);
        }
    }

    private Tokens() {
    }
}
