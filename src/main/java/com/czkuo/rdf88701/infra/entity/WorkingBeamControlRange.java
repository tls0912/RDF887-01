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
 * @since 2025-06-16
 */
@Getter
@Setter
@ToString
@TableName("working_beam_control_range")
public class WorkingBeamControlRange {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long workingBeamId;

    private Long locationPointId;

    /**
     * 位移順序（例如由前至後）
     */
    private Integer positionOrder;

    private LocalDateTime createdTime;
}
