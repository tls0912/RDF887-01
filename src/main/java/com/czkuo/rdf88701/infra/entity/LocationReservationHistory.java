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
 * 儲位預約紀錄歷史
 * </p>
 *
 * @author czkuo
 * @since 2025-06-12
 */
@Getter
@Setter
@ToString
@TableName("location_reservation_history")
public class LocationReservationHistory {

    /**
     * 歷史主鍵
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 location_reservation_record.id
     */
    private Long originId;

    private Long containerMainId;

    private Long locationPointId;

    private String reservedBy;

    private String reservedReason;

    private LocalDateTime reservedTime;

    private LocalDateTime expiredTime;

    private Boolean fulfilled;

    private LocalDateTime fulfilledTime;

    private Boolean cancelled;

    private LocalDateTime cancelledTime;

    private String cancelledReason;

    private Boolean expired;

    /**
     * 異動類型
     */
    private String changeType;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;

    /**
     * 操作者（系統或人員帳號）
     */
    private String operator;

    private String remark;
}
