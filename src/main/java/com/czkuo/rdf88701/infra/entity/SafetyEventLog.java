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
 * @since 2025-08-24
 */
@Getter
@Setter
@ToString
@TableName("safety_event_log")
public class SafetyEventLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long pointId;

    private String fromTriggered;

    private String toTriggered;

    private LocalDateTime changeTime;

    private String snapshotAfter;
}
