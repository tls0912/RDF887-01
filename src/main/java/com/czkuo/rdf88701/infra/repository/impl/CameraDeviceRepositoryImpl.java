package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.CameraDeviceRepository;
import com.czkuo.rdf88701.infra.entity.CameraDevice;
import com.czkuo.rdf88701.infra.mapper.CameraDeviceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class CameraDeviceRepositoryImpl implements CameraDeviceRepository {

    private final CameraDeviceMapper cameraDeviceMapper;

    public CameraDeviceRepositoryImpl(CameraDeviceMapper cameraDeviceMapper) {
        this.cameraDeviceMapper = cameraDeviceMapper;
    }

    @Override
    public Optional<CameraDevice> findById(Long id) {
        return Optional.ofNullable(cameraDeviceMapper.selectById(id));
    }

    @Override
    public boolean save(CameraDevice entity) {
        return cameraDeviceMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(CameraDevice entity) {
        return cameraDeviceMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return cameraDeviceMapper.deleteById(id) > 0;
    }

    @Override
    public List<CameraDevice> findAll() {
        return cameraDeviceMapper.selectList(new QueryWrapper<>());
    }
}
