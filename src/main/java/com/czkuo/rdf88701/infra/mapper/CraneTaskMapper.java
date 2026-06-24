package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <p>
 * CraneTask 資料表 Mapper 接口
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
public interface CraneTaskMapper extends BaseMapper<CraneTask> {

    /**
     * 查詢指定 craneId 下所有 PENDING 任務
     */
    List<CraneTask> selectPendingTasksByCraneId(@Param("craneId") Integer craneId);

    /**
     * 查詢指定 craneId 下第一筆 PENDING 任務（優先級 + 時間排序）
     */
    CraneTask selectNextPendingTaskByCraneId(@Param("craneId") Integer craneId);

    /**
     * 更新指定任務狀態
     */
    int updateTaskStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /**
     * 新增：標記任務為 IN_PROGRESS
     */
    int markTaskAsInProgress(@Param("id") Long id);

    /**
     * 新增：標記標記結束時間
     */
    int markTaskAsDone(@Param("id") Long id);

    /**
     * 條件查詢 CraneTask 清單
     *
     * @param requestId 請求 ID（可為 null）
     * @param craneId Crane ID（可為 null）
     * @param taskType 任務類型（可為 null）
     * @param taskStatus 任務狀態（可為 null）
     * @param priorityLevel 優先等級（可為 null）
     * @param containerMainId 容器 ID（可為 null）
     * @param sourceLocationId 來源位置 ID（可為 null）
     * @param targetLocationId 目標位置 ID（可為 null）
     * @param dispatchedAfter 派送時間起（可為 null）
     * @param dispatchedBefore 派送時間迄（可為 null）
     * @param completedAfter 完成時間起（可為 null）
     * @param completedBefore 完成時間迄（可為 null）
     * @param cancelledAfter 取消時間起（可為 null）
     * @param cancelledBefore 取消時間迄（可為 null）
     * @return 符合條件之清單
     */
    List<CraneTask> selectByCondition(
            @Param("requestId") Long requestId,
            @Param("craneId") Integer craneId,
            @Param("taskType") String taskType,
            @Param("taskStatus") String taskStatus,
            @Param("priorityLevel") Integer priorityLevel,
            @Param("containerMainId") Long containerMainId,
            @Param("sourceLocationId") Long sourceLocationId,
            @Param("targetLocationId") Long targetLocationId,
            @Param("dispatchedAfter") LocalDateTime dispatchedAfter,
            @Param("dispatchedBefore") LocalDateTime dispatchedBefore,
            @Param("completedAfter") LocalDateTime completedAfter,
            @Param("completedBefore") LocalDateTime completedBefore,
            @Param("cancelledAfter") LocalDateTime cancelledAfter,
            @Param("cancelledBefore") LocalDateTime cancelledBefore
    );

    /**
     * 查詢指定 craneName 的最高優先任務（DISPATCHED > PENDING, priority_level DESC）
     */
    CraneTask selectTopTaskByCrane(@Param("craneId") int craneId);
}
