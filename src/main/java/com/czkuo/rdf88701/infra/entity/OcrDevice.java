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
