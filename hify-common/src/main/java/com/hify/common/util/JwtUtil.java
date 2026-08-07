package com.hify.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类 —— 生成 / 解析 / 校验 Token.
 * <p>
 * MVP 阶段使用对称密钥 HS256，后续可升级为非对称密钥。
 * 密钥通过环境变量 {@code JWT_SECRET} 注入，默认值仅用于本地开发。
 * </p>
 */
@Slf4j
public final class JwtUtil {

    /** 默认密钥（至少 256 bits = 32 字节），生产环境务必通过环境变量覆盖 */
    private static final String DEFAULT_SECRET = "hify-local-dev-secret-key-2024-min-32bytes!!";
    private static final long DEFAULT_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 天

    private JwtUtil() {
    }

    /**
     * 获取签名密钥.
     * <p>
     * 生产环境通过 {@code JWT_SECRET} 环境变量（K8s Secret）注入。
     * 自动 Base64 解码（支持 256-bit 的 Base64 编码密钥），
     * 非 Base64 格式则直接用作密钥字符串。
     * </p>
     */
    private static SecretKey getKey() {
        String secret = System.getenv().getOrDefault("JWT_SECRET", DEFAULT_SECRET);
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            // 不是 Base64 编码，直接使用原始字节
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ----------------------------------------------------
    // 生成
    // ----------------------------------------------------

    /**
     * 生成 JWT Token.
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @return JWT 字符串
     */
    public static String generate(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + DEFAULT_EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getKey())
                .compact();
    }

    // ----------------------------------------------------
    // 解析
    // ----------------------------------------------------

    /**
     * 解析 JWT Token，返回 Claims.
     *
     * @param token JWT 字符串
     * @return Claims，解析失败返回 null
     */
    public static Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取用户 ID.
     *
     * @param token JWT 字符串
     * @return 用户 ID，解析失败返回 null
     */
    public static Long parseUserId(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return null;
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            log.warn("JWT subject 不是有效的数字: {}", claims.getSubject());
            return null;
        }
    }

    /**
     * 校验 Token 是否有效（未过期 + 签名正确）.
     *
     * @param token JWT 字符串
     * @return true 有效
     */
    public static boolean validate(String token) {
        return parse(token) != null;
    }
}
