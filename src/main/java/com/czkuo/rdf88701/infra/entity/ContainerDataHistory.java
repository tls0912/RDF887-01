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
@TableName("container_data_history")
public class ContainerDataHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 container_data.id
     */
    private Long originId;

    private Long containerMainId;

    private String ocrText1;

    private String ocrText2;

    private Integer estimatedQuantity;

    private Integer verifiedQuantity;

    private Integer workCoverLayers;

    private Integer coverLayers;

    private Integer productLayers;

    private String contentKind;

    private String changeType;

    private LocalDateTime archivedTime;

    private String operator;

    private String remark;
}
