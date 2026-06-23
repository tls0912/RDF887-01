package com.czkuo.rdf88701.infra.entity;

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
 * @since 2025-08-24
 */
@Getter
@Setter
@ToString
@TableName("safety_status_snapshot")
public class SafetyStatusSnapshot {

    @TableId("point_id")
    private Long pointId;

    private String isTriggered;

    private LocalDateTime lastChangeTime;

    private LocalDateTime lastPollTime;
}
