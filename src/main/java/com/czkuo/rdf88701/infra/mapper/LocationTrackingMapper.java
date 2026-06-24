package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
public interface LocationTrackingMapper extends BaseMapper<LocationTracking> {

    /**
     * 根據 flow ID 查詢對應的 entry_type
     *
     * @param flowId location_flow 表的主鍵 ID
     * @return entry_type（如 PLC、MANUAL、EXTERNAL、REBUILD），若無資料則為 null
     */
    String findEntryTypeByFlowId(Long flowId);

    /**
     * 以單一 JOIN 查詢目前所有在位容器的 alias_code
     *
     * 等價 SQL (MySQL):
     *   SELECT cm.alias_code
     *   FROM location_tracking lt
     *   JOIN container_main cm ON cm.id = lt.container_main_id
     *   WHERE cm.alias_code IS NOT NULL AND cm.alias_code <> ''
     */
    List<String> selectAllPresentAliasCodes();
    List<String> selectPresentAliasCodesNot272829();
    List<Long> selectContainersByLocations(@Param("ids") String ids);
    List<LocationTracking> selectContainersByWorkingBeamId(@Param("id") long id);

    int countEmptyOwnStorage();
}
