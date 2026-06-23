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
 * @since 2025-09-30
 */
@Getter
@Setter
@ToString
@TableName("tool_limit_override")
public class ToolLimitOverride {

    @TableId("tool_name")
    private String toolName;

    private String overrideLimit;

    private String unit;

    private Boolean isActive;

    private LocalDateTime updatedTime;
}
