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
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("product_main_history")
public class ProductMainHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 product_main.id
     */
    private Long originId;

    private String aliasCode;

    private Long containerMainId;

    private String productCode;

    private String lotNo;

    private String partNo;

    private String changeType;

    private LocalDateTime archivedTime;

    private String operator;

    private String remark;
}
