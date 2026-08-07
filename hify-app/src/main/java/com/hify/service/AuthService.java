package com.hify.service;

import com.hify.controller.dto.AuthResponse;
import com.hify.controller.dto.LoginRequest;
import com.hify.controller.dto.RegisterRequest;

/**
 * 认证业务接口.
 */
public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse register(RegisterRequest request);
}
