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
 * @since 2025-06-06
 */
@Getter
@Setter
@ToString
@TableName("crane_task_follow_up_record")
public class CraneTaskFollowUpRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 源頭任務 ID（即最初失敗任務）
     */
    private Long rootTaskId;

    /**
     * 此次補償所根據的任務 ID
     */
    private Long originalTaskId;

    /**
     * 補償原因代碼
     */
    private String reasonCode;

    /**
     * 補償原因描述
     */
    private String reasonDesc;

    /**
     * 補償產生的新任務 ID
     */
    private Long followUpTaskId;

    /**
     * 是否已處理完成
     */
    private Boolean handled;

    /**
     * 標記為已處理的時間
     */
    private LocalDateTime handledTime;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;
}
