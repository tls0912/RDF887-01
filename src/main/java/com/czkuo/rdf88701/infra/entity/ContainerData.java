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
@TableName("container_data")
public class ContainerData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 對應 container_main.id */
    private Long containerMainId;

    /** OCR 掃描結果（容器標示） */
    private String ocrText1;

    /** OCR 掃描結果（容器標示） */
    private String ocrText2;

    /** 預估層數 */
    private Integer estimatedQuantity;

    /** 驗證層數（通常 = coverLayers + productLayers） */
    private Integer verifiedQuantity;

    /** 工蓋層數（上蓋的子集合；可為 null 代表未知） */
    private Integer workCoverLayers;

    /** 上蓋層數（可為 null 代表未知） */
    private Integer coverLayers;

    /** 一般產品層數（可為 null 代表未知） */
    private Integer productLayers;

    /** 容器內容型態：UNKNOWN / NORMAL_WITH_COVER / NORMAL_NO_COVER / ALL_COVER / EMPTY */
    private String contentKind;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;
}
