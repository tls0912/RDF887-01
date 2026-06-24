package com.czkuo.rdf88701.application.dto.auth;

import lombok.Data;

/**
 * 登入回應資料
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class LoginResponse {
    private Long userId;
    private String username;
    private String roleName;
}
