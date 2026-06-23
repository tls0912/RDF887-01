package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * <p>
 * 入站 R031 MESSAGE 明細（WIP→Manual Port）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("robot_in_r031")
public class RobotInR031 {

    /**
     * 對應 mqtt_message_log.id（入站 COMMAND）
     */
    @TableId("log_id")
    private Long logId;

    private String lotId;

    private String carrierId;

    /**
     * Manual Port 儲格
     */
    private String wipName;
}
