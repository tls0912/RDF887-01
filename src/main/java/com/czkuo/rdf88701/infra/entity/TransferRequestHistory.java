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
 * Transfer 任務請求歷史表
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 */
@Getter
@Setter
@ToString
@TableName("transfer_request_history")
public class TransferRequestHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 transfer_request.id
     */
    private Long originId;

    private String requestKey;

    private Integer version;

    private String requestSource;

    /**
     * Transfer 裝置 ID
     */
    private Long transferId;

    /**
     * 任務類型
     */
    private String taskType;

    /**
     * 關聯容器（可選）
     */
    private Long containerMainId;

    private Long sourceLocationId;

    private Long targetLocationId;

    private String sourceLocationName;

    private String targetLocationName;

    private String accepted;

    private LocalDateTime acceptTime;

    private String rejectReason;

    private LocalDateTime requestTime;

    private String operator;

    private String rawPayload;

    private String remark;

    private LocalDateTime createdTime;

    /**
     * 對應主表 updated_time
     */
    private LocalDateTime updatedTime;

    /**
     * 異動類型
     */
    private String changeType;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;
}
