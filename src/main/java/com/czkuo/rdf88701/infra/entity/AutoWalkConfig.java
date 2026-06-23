package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 
 * </p>
 *
 * @author czkuo
 * @since 2025-06-07
 */
@Getter
@Setter
@ToString
@TableName("auto_walk_config")
public class AutoWalkConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 策略代碼，如 RANDOM、SEQUENTIAL、SINGLE
     */
    private String strategyCode;

    /**
     * 策略名稱（中文可讀）
     */
    private String strategyName;

    /**
     * 是否啟用該策略
     */
    private Boolean enabled;

    /**
     * 每輪最多搬幾個容器（NULL 表示不限）
     */
    private Integer containerLimit;

    /**
     * 排除的 container_main_id 列表（JSON 陣列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> excludedContainerIds;

    /**
     * 策略參數擴充用，例如排除哪些儲位等
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraConfig;

    /**
     * 建立時間
     */
    private LocalDateTime createdTime;

    /**
     * 更新時間
     */
    private LocalDateTime updatedTime;
}
