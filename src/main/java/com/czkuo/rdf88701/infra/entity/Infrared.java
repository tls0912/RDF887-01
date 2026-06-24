package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
 * @since 2025-08-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
public class Infrared {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 設備名稱，如 Infrared#1
     */
    private String name;

    /**
     * 是否啟用（1=啟用，0=停用）
     */
    private Boolean enabled;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;
}
