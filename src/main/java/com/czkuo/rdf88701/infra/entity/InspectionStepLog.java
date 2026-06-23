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
 * 異物檢步驟追蹤
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Getter
@Setter
@ToString
@TableName("inspection_step_log")
public class InspectionStepLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long jobId;

    /**
     * 1=FIRST, 2=SECOND
     */
    private Byte stepNo;

    private Long stationId;

    /**
     * MOVE/TRIGGER/COMPLETE/ERROR
     */
    private String action;

    /**
     * IDLE/FIRST_IN_PROGRESS/…
     */
    private String cameraState;

    private String cameraError;

    private Integer countFirst;

    private Integer countSecond;

    private Integer countTotal;

    private Integer times;

    /**
     * 如需存完整回讀
     */
    private String payloadJson;

    private LocalDateTime createdTime;
}
