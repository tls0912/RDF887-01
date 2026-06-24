package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * <p>
 * 入站 R029 LOT 清單
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("robot_in_r029_lot")
public class RobotInR029Lot {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 robot_in_r029.log_id（= mqtt_message_log.id）
     */
    private Long logId;

    /**
     * CARRIERID（修正命名）
     */
    private String carrierId;
}
