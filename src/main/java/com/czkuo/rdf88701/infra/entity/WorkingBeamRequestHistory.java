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
 * @since 2025-06-21
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("working_beam_request_history")
public class WorkingBeamRequestHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 working_beam_request.id
     */
    private Long originId;

    private String requestKey;

    private Integer version;

    private String requestSource;

    private Long workingBeamId;

    private String direction;

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
