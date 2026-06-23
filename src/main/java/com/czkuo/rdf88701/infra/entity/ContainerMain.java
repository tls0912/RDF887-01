package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * ContainerMain Entity
 * - 主容器資料（對應 container_main 資料表）
 * - 包含對應最新的 ContainerData
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Getter
@Setter
@ToString
@TableName("container_main")
public class ContainerMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 容器顯示 / 邏輯代號（alias）
     */
    private String aliasCode;

    /**
     * 實體容器類型
     */
    private String containerType;

    /**
     * 條碼
     */
    private String containerCode;

    /**
     * 批號
     */
    private String lotNo;

    /**
     * 料號
     */
    private String partNo;

    /**
     * 容器狀態（生命週期）
     */
    private String state;

    /**
     * 關閉時間
     */
    private LocalDateTime closedTime;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;

    /**
     * 最新 container_data（由查詢聚合帶入）
     */
    @TableField(exist = false)
    private ContainerData latestData;
}
