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
 * 異物檢工作流主檔（保證每支夾爪同時僅一筆進行中）
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
@TableName("inspection_job")
public class InspectionJob {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * UUID
     */
    private String jobKey;

    private Long gripperId;

    /**
     * 當時夾爪上帳（便於追蹤）
     */
    private Long containerMainId;

    /**
     * 觸發異物檢的來源站（如 Site#35）
     */
    private String originSiteName;

    private Long firstStationId;

    private Long secondStationId;

    private Long cameraId;

    /**
     * CREATED/MOVE_TO_FIRST/WAIT_AT_FIRST/…/DONE/FAILED
     */
    private String status;

    /**
     * 0=進行中, 1=關閉(成功或失敗)
     */
    private Boolean isClosed;

    private String failReason;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
