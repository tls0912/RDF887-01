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
 * 異物檢虛擬站（含拍照順序與綁定相機/夾爪）
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("inspection_station")
public class InspectionStation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 location_point.id
     */
    private Long locationPointId;

    /**
     * VIRTUAL#6 / 7 / 8 / 9
     */
    private String name;

    /**
     * 1=FIRST, 2=SECOND
     */
    private Byte shotOrder;

    /**
     * camera_device.id
     */
    private Long cameraId;

    /**
     * 此站點由哪支夾爪進站拍照（如 4 or 5）
     */
    private Long gripperId;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
