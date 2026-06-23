package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.AlarmItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-26
 */
@Mapper
public interface AlarmItemMapper extends BaseMapper<AlarmItem> {

    // ===== 快查 =====

    /**
     * 依 global_code 取單筆（LIMIT 1）
     */
    AlarmItem selectByGlobalCode(@Param("code") int globalCode);

    // ===== 佇列領取（行鎖 + SKIP LOCKED）=====

    /**
     * 以行鎖 + SKIP LOCKED 領取「待送 PLC」的工作。
     * 條件：want_plc_trigger=1 AND allow_plc_trigger=1 AND enabled=1
     * 只取必要欄位以降低回表成本（見 XML 中的 SELECT 欄位）。
     * <p><b>必須在 @Transactional 交易中呼叫！</b></p>
     *
     * @param limit 取得筆數上限
     */
    List<AlarmItem> lockAndFetchPending(@Param("limit") int limit);

    // ===== 值變才更新 =====

    /**
     * 將 is_triggered 設為指定值；僅在值不同時才 UPDATE。
     *
     * @return 受影響列數（>0 代表真的有改變）
     */
    int setTriggeredIfChanged(@Param("code") int globalCode, @Param("val") boolean value);

    /**
     * 將 want_plc_trigger 設為指定值；僅在值不同、且 enabled=1 & allow_plc_trigger=1 時才 UPDATE。
     *
     * @return 受影響列數（>0 代表真的有改變）
     */
    int setWantPlcIfAllowed(@Param("code") int globalCode, @Param("on") boolean on);

    // ===== 批次操作 =====

    /**
     * 批次清除 want_plc_trigger=0（通常在送 PLC 成功後立即清除）
     *
     * @param ids alarm_item.id 清單
     */
    int clearWantPlcByIds(@Param("ids") List<Long> ids);

    /**
     * 批次回補佇列（送 PLC 失敗重試用）；僅在目前為 0 且允許/啟用時置為 1
     *
     * @param globalCodes alarm_item.global_code 清單
     */
    int reenqueueForPlcByGlobalCodes(@Param("codes") List<Integer> globalCodes);
}
