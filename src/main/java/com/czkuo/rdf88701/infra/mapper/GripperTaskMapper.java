package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.application.dto.GripperTaskWithContainerDTO;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface GripperTaskMapper extends BaseMapper<GripperTask> {

    /**
     * 查詢 Gripper 任務及其容器資訊（JOIN container_main），可依條件過濾。
     * 對應 XML 中的 selectWithContainerByCondition 查詢。
     *
     * @param gripperId     Gripper 裝置 ID（可選）
     * @param taskStatus    任務狀態，例如 PENDING / RUNNING / COMPLETED（可選）
     * @param createdAfter  建立時間下限（含，格式：yyyy-MM-ddTHH:mm:ss）（可選）
     * @param createdBefore 建立時間上限（含，格式：yyyy-MM-ddTHH:mm:ss）（可選）
     * @return 符合條件的任務及容器資訊清單，封裝為 DTO
     */
    List<GripperTaskWithContainerDTO> selectWithContainerByCondition(
            @Param("gripperId") Integer gripperId,
            @Param("taskStatus") String taskStatus,
            @Param("createdAfter") LocalDateTime createdAfter,
            @Param("createdBefore") LocalDateTime createdBefore
    );

    /**
     * 查詢指定 Gripper 裝置的當前最優先任務（DISPATCHED > PENDING）
     */
    GripperTask findTopTaskByGripperOrdered(@Param("gripperId") int gripperId);

    /**
     * 查詢該容器在此 Gripper 上最後一筆有效 PICK 任務
     */
    GripperTask findLastPickTaskByContainer(@Param("gripperId") Long gripperId,
                                            @Param("containerMainId") Long containerMainId);
}
