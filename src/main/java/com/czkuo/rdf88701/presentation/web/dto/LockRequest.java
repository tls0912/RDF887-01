package com.czkuo.rdf88701.presentation.web.dto;

import lombok.Data;

@Data
public class LockRequest {
    /** 鎖定原因（可選） */
    private String reason;
}
