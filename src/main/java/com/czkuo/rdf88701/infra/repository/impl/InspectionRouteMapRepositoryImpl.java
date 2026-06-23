package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.InspectionRouteMapRepository;
import com.czkuo.rdf88701.infra.entity.InspectionRouteMap;
import com.czkuo.rdf88701.infra.mapper.InspectionRouteMapMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InspectionRouteMapRepositoryImpl implements InspectionRouteMapRepository {

    private final InspectionRouteMapMapper inspectionRouteMapMapper;

    public InspectionRouteMapRepositoryImpl(InspectionRouteMapMapper inspectionRouteMapMapper) {
        this.inspectionRouteMapMapper = inspectionRouteMapMapper;
    }

    @Override
    public Optional<InspectionRouteMap> findById(Long id) {
        return Optional.ofNullable(inspectionRouteMapMapper.selectById(id));
    }

    @Override
    public boolean save(InspectionRouteMap entity) {
        return inspectionRouteMapMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(InspectionRouteMap entity) {
        return inspectionRouteMapMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return inspectionRouteMapMapper.deleteById(id) > 0;
    }

    @Override
    public List<InspectionRouteMap> findAll() {
        return inspectionRouteMapMapper.selectList(new QueryWrapper<>());
    }

    // ===== 新增：給 GP5 / Orchestrator 用 =====

    @Override
    public Optional<InspectionRouteMap> findByGripperId(Long gripperId) {
        return Optional.ofNullable(
                inspectionRouteMapMapper.selectOne(
                        new QueryWrapper<InspectionRouteMap>()
                                .eq("gripper_id", gripperId)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public Optional<InspectionRouteMap> findByGripperIdEnabled(Long gripperId) {
        return Optional.ofNullable(
                inspectionRouteMapMapper.selectOne(
                        new QueryWrapper<InspectionRouteMap>()
                                .eq("gripper_id", gripperId)
                                .eq("enabled", 1)
                                .last("LIMIT 1")
                )
        );
    }

    @Override
    public boolean existsForGripper(Long gripperId) {
        Long cnt = inspectionRouteMapMapper.selectCount(
                new QueryWrapper<InspectionRouteMap>()
                        .eq("gripper_id", gripperId)
        );
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean saveOrUpdateByGripper(InspectionRouteMap entity) {
        // 需要 entity.getGripperId() 有值
        if (entity.getGripperId() == null) {
            throw new IllegalArgumentException("gripperId is required for saveOrUpdateByGripper");
        }

        // 先查是否存在
        InspectionRouteMap exist = inspectionRouteMapMapper.selectOne(
                new QueryWrapper<InspectionRouteMap>()
                        .eq("gripper_id", entity.getGripperId())
                        .last("LIMIT 1")
        );

        if (exist == null) {
            // INSERT
            return inspectionRouteMapMapper.insert(entity) > 0;
        } else {
            // UPDATE by gripper_id（避免誤用 id）
            UpdateWrapper<InspectionRouteMap> uw = new UpdateWrapper<InspectionRouteMap>()
                    .eq("gripper_id", entity.getGripperId());
            // 直接用 entity 覆蓋可更新欄位（MP 會用非 null 欄位 set）
            return inspectionRouteMapMapper.update(entity, uw) > 0;
        }
    }

    @Override
    public List<InspectionRouteMap> findAllEnabled() {
        return inspectionRouteMapMapper.selectList(
                new QueryWrapper<InspectionRouteMap>()
                        .eq("enabled", 1)
        );
    }
}
