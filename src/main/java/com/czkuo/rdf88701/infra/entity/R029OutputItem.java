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
 * R029 產出與上架追蹤（逐新載具；狀態欄為 state）
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Getter
@Setter
@ToString
@TableName("r029_output_item")
public class R029OutputItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 對應 robot_r029_task.id
     */
    private Long taskId;

    /**
     * 來源 CARRIER（原 alias/lot）
     */
    private String fromCarrierId;

    /**
     * 拆併後新 CARRIER（新序號/新載具）
     */
    private String newCarrierId;

    /**
     * 此新載具實際承載片數
     */
    private Integer pieces;

    /**
     * 原 zipb_state 改為 state
     */
    private String state;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
