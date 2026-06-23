package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


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
@TableName("tool_catalog")
public class ToolCatalog {

    @TableId("tool_name")
    private String toolName;

    private String defaultLimit;

    private String unit;

    private String remark;
}
