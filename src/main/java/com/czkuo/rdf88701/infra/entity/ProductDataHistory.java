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
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("product_data_history")
public class ProductDataHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 product_data.id
     */
    private Long originId;

    private Long productMainId;

    private String ocrText;

    private Integer layerIndex;

    private String qualityCheckResult;

    private Boolean isLid;

    private String changeType;

    private LocalDateTime archivedTime;

    private String operator;

    private String remark;
}
