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
 * Infrared 任務請求歷史記錄
 * </p>
 *
 * @author czkuo
 * @since 2025-08-06
 */
@Getter
@Setter
@ToString
@TableName("infrared_request_history")
public class InfraredRequestHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 infrared_request.id
     */
    private Long originId;

    private String requestKey;

    private Integer version;

    private String requestSource;

    private Long infraredId;

    private Long containerMainId;

    /**
     * 任務類型
     */
    private String taskType;

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
