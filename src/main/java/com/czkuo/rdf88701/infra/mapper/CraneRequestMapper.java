package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
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
public interface CraneRequestMapper extends BaseMapper<CraneRequest> {

    /**
     * 根據條件查詢 Crane Request 清單
     *
     * @param requestType 請求類型（可為 null）
     * @param requestSource 請求來源（可為 null）
     * @param accepted 是否接受（Y/N，可為 null）
     * @param requestAfter 請求時間起（可為 null）
     * @param requestBefore 請求時間迄（可為 null）
     * @return 符合條件之清單
     */
    List<CraneRequest> selectByCondition(
            @Param("requestType") String requestType,
            @Param("requestSource") String requestSource,
            @Param("accepted") String accepted,
            @Param("requestAfter") LocalDateTime requestAfter,
            @Param("requestBefore") LocalDateTime requestBefore
    );
}
