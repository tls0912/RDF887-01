package com.czkuo.rdf88701.common.dto;

import com.czkuo.rdf88701.common.enums.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 API 回傳格式
 *
 * @param <T> 資料型別
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseResult<T> {

    private boolean success;
    private String message;
    private T data;
    private Integer code; // 可選擇加入錯誤碼系統

    // === 成功回傳 ===

    public static <T> ResponseResult<T> ok() {
        return new ResponseResult<>(true, "success", null, 0);
    }

    public static <T> ResponseResult<T> ok(T data) {
        return new ResponseResult<>(true, "success", data, 0);
    }

    public static <T> ResponseResult<T> ok(String message, T data) {
        return new ResponseResult<>(true, message, data, 0);
    }

    // === 失敗回傳 ===

    public static <T> ResponseResult<T> fail(String message) {
        return new ResponseResult<>(false, message, null, -1);
    }

    public static <T> ResponseResult<T> fail(String message, int code) {
        return new ResponseResult<>(false, message, null, code);
    }

    public static <T> ResponseResult<T> fail(String message, T data, int code) {
        return new ResponseResult<>(false, message, data, code);
    }

    public static <T> ResponseResult<T> failFrom(ResultCode resultCode) {
        return new ResponseResult<>(false, resultCode.getMessage(), null, resultCode.getCode());
    }
}
