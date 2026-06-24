package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * Infrared 任務執行 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface InfraredTaskMapper extends BaseMapper<InfraredTask> {

    /**
     * 查詢指定 Infrared 裝置當前最優先任務（交由 Mapper 處理排序）
     * 條件：
     * - 任務狀態為 DISPATCHED 或 PENDING
     * - 排序依據：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     *
     * @param infraredId 裝置 ID
     * @return 最優先任務（若有）
     */
    InfraredTask findTopTaskByInfraredOrdered(@Param("infraredId") int infraredId);

}
