package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

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
@TableName("tt_record_item")
public class TtRecordItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Integer stepNo;

    private String stepName;

    private Integer rawValue;

    private BigDecimal timeSec;

    private String remarkId;
}
