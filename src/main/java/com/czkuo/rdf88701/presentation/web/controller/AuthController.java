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
