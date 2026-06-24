package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-09-04
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("ocr_device")
public class OcrDevice {

    private Integer id;

    private String name;

    private String status;

    private Boolean acceptingTask;

    private LocalDateTime lastActiveTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
