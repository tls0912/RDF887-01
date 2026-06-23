package com.czkuo.rdf88701.common.enums;

import lombok.Getter;

/**
 * 常見錯誤碼定義（業務與系統錯誤）
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    FAILURE(-1, "failure"),
    VALIDATION_ERROR(1001, "validation error"),
    UNAUTHORIZED(1002, "unauthorized"),
    FORBIDDEN(1003, "forbidden"),
    NOT_FOUND(1004, "not found"),
    INTERNAL_ERROR(9999, "internal server error");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
