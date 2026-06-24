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
 * @since 2025-05-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Setter
@ToString
@TableName("container_main_history")
public class ContainerMainHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應原始 container_main.id
     */
    private Long originId;

    /**
     * 虛擬容器代號（系統唯一編號）
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
     * 容器狀態（生命週期狀態）
     */
    private String state;

    /**
     * 關閉時間
     */
    private LocalDateTime closedTime;

    /**
     * 異動類型
     */
    private String changeType;

    /**
     * 歸檔時間
     */
    private LocalDateTime archivedTime;

    /**
     * 操作者
     */
    private String operator;

    /**
     * 備註
     */
    private String remark;
}
