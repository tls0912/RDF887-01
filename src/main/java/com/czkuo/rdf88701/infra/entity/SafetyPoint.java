package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * @since 2025-08-24
 */
@Getter
@Setter
@ToString
@TableName("safety_point")
public class SafetyPoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String groupWord;

    private String bitHex;

    private String addrExpr;

    private String typeCode;

    private String pointName;

    private String remark;

    private String enabled;
}
