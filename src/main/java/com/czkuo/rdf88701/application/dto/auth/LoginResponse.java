package com.czkuo.rdf88701.application.dto.auth;

import lombok.Data;

/**
 * 登入回應資料
 */
@Data
public class LoginResponse {
    private Long userId;
    private String username;
    private String roleName;
}
