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
 * 虛擬容器-屬性對應表（可彈性擴充各式欄位）
 * </p>
 *
 * @author czkuo
 * @since 2025-08-24
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("container_attr")
public class ContainerAttr {

    /**
     * 主鍵 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 container_main.id
     */
    private Long containerMainId;

    /**
     * 屬性名稱（如 thickness_mm、height_mm、weight_g）
     */
    private String attrKey;

    /**
     * 屬性值（可文字/數字/JSON）
     */
    private String attrValue;

    /**
     * 屬性單位（如 mm、g、pcs，選填）
     */
    private String unit;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 最後更新時間
     */
    private LocalDateTime updatedTime;
}
