package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
@TableName("ocr_task")
public class OcrTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer ocrDeviceId;

    private Long containerMainId;

    private String status;

    private LocalDateTime createdTime;

    private LocalDateTime startedTime;

    private LocalDateTime completedTime;

    private String ocrText1;

    private String ocrText2;

    private String errorMessage;

    private Integer timingCaptureMs;

    private Integer timingOcrProcessingMs;

    private Integer timingPackagingMs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
