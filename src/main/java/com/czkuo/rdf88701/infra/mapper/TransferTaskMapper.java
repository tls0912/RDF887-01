package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * Transfer 任務執行表 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 */
@Mapper
public interface TransferTaskMapper extends BaseMapper<TransferTask> {

    /**
     * 查詢指定 Transfer 裝置當前最優先任務（交由 Mapper 處理排序）
     * 條件：
     * - 任務狀態為 DISPATCHED 或 PENDING
     * - 排序依據：DISPATCHED > PENDING、priority_level DESC、created_time ASC
     *
     * @param transferId 裝置 ID
     * @return 最優先任務（若有）
     */
    TransferTask findTopTaskByTransferOrdered(@Param("transferId") int transferId);
}
