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
 * @author matt
 * @since 2026-02-12
 */
@Getter
@Setter
@ToString
@TableName("alarm_action_log")
public class AlarmActionLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long globalCode;
    private String actionNote;
    private String aseCheck;
    private String importTime;
}
