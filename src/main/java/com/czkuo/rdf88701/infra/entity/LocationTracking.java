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
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("location_tracking")
public class LocationTracking {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long containerMainId;

    private Long locationPointId;

    /**
     * 抵達時間（建帳時間）
     */
    private LocalDateTime arrivedTime;

    /**
     * 最後一次驗證位置的時間（來自 PLC 或人工）
     */
    private LocalDateTime lastVerifiedTime;

    /**
     * 最後異動時間
     */
    private LocalDateTime updatedTime;

    /**
     * 來源 flow 紀錄 ID（參考用途，不加 FK）
     */
    private Long flowId;
}
