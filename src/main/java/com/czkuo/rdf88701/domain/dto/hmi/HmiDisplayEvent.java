package com.czkuo.rdf88701.domain.dto.hmi;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HmiDisplayEvent {
    private Long id;
    private String tid;
    private String msgEn;
    private String msgCh;
    private String status; // SENT / FAILED

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;
}
