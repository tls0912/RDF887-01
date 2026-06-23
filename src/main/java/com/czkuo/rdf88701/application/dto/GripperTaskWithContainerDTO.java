package com.czkuo.rdf88701.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 包含容器資訊的 Gripper 任務 DTO
 */
@Data
public class GripperTaskWithContainerDTO {

    private Long id;
    private Integer gripperId;
    private String taskType;
    private String taskStatus;
    private Long containerMainId;

    private String aliasCode;
    private String containerType;

    private LocalDateTime createdTime;
    private LocalDateTime completedTime;
}
