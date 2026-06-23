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
@TableName("location_flow")
public class LocationFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long containerMainId;

    private Long locationPointId;

    /**
     * 帳務建立方式
     */
    private String entryType;

    /**
     * 帳務離開方式
     */
    private String exitType;

    /**
     * 進入時間
     */
    private LocalDateTime arrivedTime;

    /**
     * 離開時間（NULL 表示尚未離開）
     */
    private LocalDateTime leftTime;

    /**
     * 進帳操作者
     */
    private String entryOperator;

    /**
     * 出帳操作者
     */
    private String exitOperator;

    /**
     * 來源任務 ID（如有）
     */
    private Long sourceTaskId;

    /**
     * 備註
     */
    private String remark;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;
}
