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
 * 異物檢路線對應：夾爪→(FIRST站, SECOND站, 相機)
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Getter
@Setter
@ToString
@TableName("inspection_route_map")
public class InspectionRouteMap {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long gripperId;

    /**
     * inspection_station.id (shot_order=1)
     */
    private Long firstStationId;

    /**
     * inspection_station.id (shot_order=2)
     */
    private Long secondStationId;

    /**
     * 冗餘存一份，便於查詢
     */
    private Long cameraId;

    private Boolean enabled;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
