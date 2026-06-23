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
@TableName("product_data")
public class ProductData {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 product_main.id
     */
    private Long productMainId;

    /**
     * 單片 OCR 結果
     */
    private String ocrText;

    /**
     * 所在容器中的層數索引（從下至上）
     */
    private Integer layerIndex;

    /**
     * 異物檢結果
     */
    private String qualityCheckResult;

    /**
     * 是否為上蓋片
     */
    private Boolean isLid;

    private LocalDateTime createdTime;
}
