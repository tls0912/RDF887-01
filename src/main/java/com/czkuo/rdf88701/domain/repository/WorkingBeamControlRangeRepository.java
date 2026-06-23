package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.WorkingBeamControlRange;
import java.util.List;
import java.util.Optional;

/**
 * WorkingBeam 控制範圍 Repository
 * - 管理 WorkingBeam 與控制位置（location_point）之間的對應關係
 * - 提供基本 CRUD 與依照 WorkingBeam 查詢控制點位順序等功能
 */
public interface WorkingBeamControlRangeRepository {

    /**
     * 根據主鍵查詢單筆資料
     */
    Optional<WorkingBeamControlRange> findById(Long id);

    /**
     * 新增一筆控制範圍資料
     */
    boolean save(WorkingBeamControlRange entity);

    /**
     * 更新一筆控制範圍資料
     */
    boolean update(WorkingBeamControlRange entity);

    /**
     * 根據主鍵刪除資料
     */
    boolean deleteById(Long id);

    /**
     * 查詢全部控制範圍資料（通常僅限管理介面使用）
     */
    List<WorkingBeamControlRange> findAll();

    /**
     * 根據 WorkingBeam ID 查詢對應控制範圍（不保證順序）
     */
    List<WorkingBeamControlRange> findByWorkingBeamId(Long workingBeamId);

    /**
     * 根據 WorkingBeam ID 查詢控制範圍，依照位移順序 position_order 排序
     * - 用於任務處理時計算搬運起點與終點
     */
    List<WorkingBeamControlRange> findByWorkingBeamIdOrderByPositionOrder(Long workingBeamId);

    /**
     * 根據位置 ID 查詢是哪個 WorkingBeam 控制的（反查用途）
     */
    Optional<WorkingBeamControlRange> findByLocationPointId(Long locationPointId);
}
