package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * WorkingBeam 任務執行 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface WorkingBeamTaskMapper extends BaseMapper<WorkingBeamTask> {

    /**
     * 查詢指定 Working Beam 裝置當前最優先任務（交由 Mapper 處理排序）
     * 條件：
     * - 任務狀態為 DISPATCHED 或 PENDING
     * - 排序依據：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     *
     * @param workingBeamId 裝置 ID
     * @return 最優先任務（若有）
     */
    WorkingBeamTask findTopTaskByWorkingBeamOrdered(@Param("workingBeamId") int workingBeamId);
}
