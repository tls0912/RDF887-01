package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.domain.dto.wip.WipSlotDetailDTO;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Mapper
public interface LocationPointMapper extends BaseMapper<LocationPoint> {

    /**
     * 更新 is_occupied 狀態
     *
     * @param locationPointId 位置主鍵 ID
     * @param occupiedFlag    佔用狀態（Y / N）
     */
    void updateOccupiedFlag(@Param("id") Long locationPointId, @Param("flag") String occupiedFlag);

    /**
     * 隨機查詢未被有效預約的可用儲位，排除指定儲位 ID 清單
     * - 使用 reservation_record 判斷有效預約 (fulfilled=0 AND cancelled=0 AND expired=0)
     * - 排除已佔用、鎖定、預約的儲位
     *
     * @param params 包含 limit 和 excludedLocationIds
     * @return 可用儲位清單
     */
    List<LocationPoint> findAvailableStorageWithoutReservationExcluding(@Param("params") Map<String, Object> params);

    /**
     * 根據 location name 查詢對應位置的主鍵 ID
     *
     * @param name 位置代碼（如 SITE#1）
     * @return 對應位置的 ID（若無則為 null）
     */
    Long selectIdByName(@Param("name") String name);

    /**
     * 根據 transfer 裝置 ID 查詢其所屬的所有位置點 ID
     * - 通常適用於 Transfer#1 控制多個位置的情境
     *
     * @param transferId Transfer 裝置 ID
     * @return 該 Transfer 對應的所有位置點主鍵清單
     */
    List<Long> selectIdsByTransferId(@Param("transferId") Long transferId);

    /**
     * 根據 gripper 裝置 ID 查詢其所屬的所有位置點 ID
     * - 通常適用於 Gripper#1 控制多個位置的情境
     *
     * @param gripperId Gripper 裝置 ID
     * @return 該 Gripper 對應的所有位置點主鍵清單
     */
    List<Long> selectIdsByGripperId(@Param("gripperId") Long gripperId);

    /**
     * 查詢所有儲格詳細現況（含帳籍/物理佔用、容器、產品、OCR 等）
     * - 多表 Join：location_point、location_tracking、container_main、container_data
     * - 主要用於 UI 查詢、S004 指令回覆、盤點現況
     *
     * @return 所有儲格的詳細現況清單
     */
    List<WipSlotDetailDTO> selectAllWipSlotDetails();

    /**
     * 查詢指定區域下的儲格詳細現況（含帳籍/物理佔用、容器、產品等）
     * - 常用於分區盤點、區域 UI 展示
     *
     * @param zoneCode 區域代碼（如 "A1"、"B2"）
     * @return 指定區域的儲格現況清單
     */
    List<WipSlotDetailDTO> selectWipSlotDetailsByZone(@Param("zoneCode") String zoneCode);

    /**
     * 用多筆 location_point_id 查詢對應位置的container_main_id
     *
     * @return 帳實不一致的儲格現況清單
     */
    List<WipSlotDetailDTO> selectMismatchedSlotDetails();

}
