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
 * @since 2025-08-26
 */
@Getter
@Setter
@ToString
@TableName("alarm_item_log")
public class AlarmItemLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Integer globalCode;

    private String titleZh;

    private String titleEn;

    private String eventType;

    private LocalDateTime createdAt;
}
