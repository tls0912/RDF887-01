package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotInR029Lot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 入站 R029 LOT 清單 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface RobotInR029LotMapper extends BaseMapper<RobotInR029Lot> {

    /** 批量插入（MySQL：INSERT IGNORE） */
    int bulkInsertIgnore(@Param("logId") Long logId, @Param("lotIds") List<String> lotIds);

    /** 單純取 lot_id 清單（避免撈整行） */
    List<String> selectLotIdsByLogId(@Param("logId") Long logId);
    List<String> selectIdByLotId(@Param("carrierId") String carrierId);
}
