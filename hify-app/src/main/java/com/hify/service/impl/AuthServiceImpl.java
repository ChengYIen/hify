package com.hify.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.entity.UserEntity;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.mapper.UserMapper;
import com.hify.common.util.JwtUtil;
import com.hify.controller.dto.AuthResponse;
import com.hify.controller.dto.LoginRequest;
import com.hify.controller.dto.RegisterRequest;
import com.hify.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务实现.
 * <p>
 * 使用 Hutool BCrypt 做密码哈希，JwtUtil（静态方法）生成 JWT。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.AUTH_CREDENTIALS_INVALID);
        }
        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        UserEntity existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, request.getUsername()));
        if (existingUser != null) {
            throw new BizException(ErrorCode.AUTH_USERNAME_EXISTS, "username=" + request.getUsername());
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername());
        user.setRole("USER");
        userMapper.insert(user);

        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return AuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .token(token)
                .build();
    }
}
