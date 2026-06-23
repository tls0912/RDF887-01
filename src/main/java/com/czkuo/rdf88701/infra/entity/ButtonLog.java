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
 */
@Getter
@Setter
@ToString
@TableName("button_log")
public class ButtonLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 來源區域：WIP / PACK(拆併) 等
     */
    private String area;

    /**
     * PLC 流水號 index（每次+1）
     */
    private Integer seqIndex;

    /**
     * 按鈕 ID：1=啟動 2=停止 3=異常復歸 4=手自動切換 5=拆併區維修門 6=打帶機#1維修門 7=打帶機#2維修門 8=打帶機#3維修門 9=貼標機維修門
     */
    private Byte buttonId;

    /**
     * 1=OK, 2=NG
     */
    private Byte returnCode;

    /**
     * PLC 時間戳 (YYMM/DDhh/mmss)
     */
    private LocalDateTime eventTime;

    private LocalDateTime createdTime;
}
