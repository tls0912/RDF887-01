package com.czkuo.rdf88701.application.dto.auth;

import lombok.Data;

/**
 * 登入請求資料
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
