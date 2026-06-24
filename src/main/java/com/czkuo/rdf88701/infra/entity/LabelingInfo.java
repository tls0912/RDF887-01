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
 * @since 2025-08-23
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("labeling_info")
public class LabelingInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 去重鍵：TID#index
     */
    private String requestKey;

    /**
     * S065 / S066
     */
    private String sourceCmdId;

    private String tid;

    private Long containerMainId;

    private String siteCode;

    private Integer labelNo;

    /**
     * 原始/歸一化資料
     */
    private String payload;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
