package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InspectionRouteMap;
import java.util.List;
import java.util.Optional;

public interface InspectionRouteMapRepository {

    Optional<InspectionRouteMap> findById(Long id);

    boolean save(InspectionRouteMap entity);

    boolean update(InspectionRouteMap entity);

    boolean deleteById(Long id);

    List<InspectionRouteMap> findAll();

    // ===== 新增：給 GP5 / Orchestrator 用 =====

    /** 依 gripper 取路線（若有 enabled 欄位建議使用 findByGripperIdEnabled） */
    Optional<InspectionRouteMap> findByGripperId(Long gripperId);

    /** 依 gripper 取啟用路線（enabled=1） */
    Optional<InspectionRouteMap> findByGripperIdEnabled(Long gripperId);

    /** 該 gripper 是否已有路線設定 */
    boolean existsForGripper(Long gripperId);

    /** 若該 gripper 已存在則 update，否則 insert（簡易 upsert） */
    boolean saveOrUpdateByGripper(InspectionRouteMap entity);

    /** 取所有啟用路線（enabled=1） */
    List<InspectionRouteMap> findAllEnabled();
}
