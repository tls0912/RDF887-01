package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.auth.LoginRequest;
import com.czkuo.rdf88701.application.dto.auth.LoginResponse;
import com.czkuo.rdf88701.application.service.auth.AuthService;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Auth REST API Controller。
 *
 * <p>提供 `/api/auth/login` 登入入口，將認證流程委派給 AuthService。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 使用者登入
     */
    @PostMapping("/login")
    public ResponseResult<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseResult.ok(response);
    }
}
