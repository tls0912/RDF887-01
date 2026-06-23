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
 * @since 2025-12-11
 */
@Getter
@Setter
@ToString
@TableName("tt_signal_def")
public class TtSignalDef {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String deviceType;

    private String deviceName;

    private String plcWord;

    private Integer stepNo;

    private String stepName;

    private Boolean isTime;

    private Integer unitDivisor;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String plcArea;
    private Long locationPoint;
    private String deviceArea;
}
