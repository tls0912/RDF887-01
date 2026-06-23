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
 * S068 打帶前狀態確認結果
 * </p>
 *
 * @author czkuo
 * @since 2025-08-25
 */
@Getter
@Setter
@ToString
@TableName("strapping_precheck_result")
public class StrappingPrecheckResult {

    /**
     * PK
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 S068 的 TID（唯一）
     */
    private String tid;

    /**
     * ACK 的結果：OK / NG
     */
    private String result;

    /**
     * 補充說明
     */
    private String resultMessage;

    /**
     * 建立時間（通常是 ACK 時間）
     */
    private LocalDateTime createdTime;
}
