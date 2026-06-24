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
 * @since 2025-12-11
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("tt_record")
public class TtRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String deviceType;

    private String deviceName;

    private String plcGroup;

    private String ttIndex;

    private Integer transferNo;

    private LocalDateTime createdTime;
    private String remarkId;
    private String deviceArea;

}
