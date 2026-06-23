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
 * 相機裝置主檔
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Getter
@Setter
@ToString
@TableName("camera_device")
public class CameraDevice {

    /**
     * 相機ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Cam1 / Cam2 ...
     */
    private String name;

    /**
     * Modbus unitId（如有分站）
     */
    private Integer modbusUnitId;

    private String description;

    private Boolean enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
