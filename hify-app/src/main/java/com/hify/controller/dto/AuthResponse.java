package com.hify.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 认证响应.
 */
@Data
@Builder
public class AuthResponse {

    private Long userId;
    private String username;
    private String displayName;
    private String token;
}
