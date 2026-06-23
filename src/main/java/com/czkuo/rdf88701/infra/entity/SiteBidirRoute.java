package com.czkuo.rdf88701.infra.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 雙向站點路徑選擇（告知 walker 出到哪個站點）
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 */
@Getter
@Setter
@ToString
@TableName("site_bidir_route")
public class SiteBidirRoute {

    /**
     * 站點對組（例：SITE15_16）
     */
    @TableId("pair_code")
    private String pairCode;

    /**
     * 當前目標站點（例：Site#15 或 Site#16）
     */
    private String activeTarget;

    /**
     * 最後異動者
     */
    private String updatedBy;

    /**
     * 最後異動時間
     */
    private LocalDateTime updatedTime;
}
