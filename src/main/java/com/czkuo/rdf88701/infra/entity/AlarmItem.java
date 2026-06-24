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
 * @since 2025-08-26
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("alarm_item")
public class AlarmItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer globalCode;

    private String type;

    private String equipment;

    private Integer localCode;

    private String titleZh;

    private String titleEn;

    private Boolean enabled;

    private Boolean allowPlcTrigger;

    private Boolean isTriggered;

    private Boolean wantPlcTrigger;

    private LocalDateTime updatedAt;
}
