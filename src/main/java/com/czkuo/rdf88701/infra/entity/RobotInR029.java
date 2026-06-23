package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * <p>
 * 入站 R029 MESSAGE 主檔（拆併打帶）
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 */
@Getter
@Setter
@ToString
@TableName("robot_in_r029")
public class RobotInR029 {

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    @TableId("log_id")
    private Long logId;

    /**
     * 批數（payload 雖為字串，這裡用數字存）
     */
    private Integer count;

    private String trayType;

    private String trayDesc;
}
