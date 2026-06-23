package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.OcrDeviceRepository;
import com.czkuo.rdf88701.infra.entity.OcrDevice;
import com.czkuo.rdf88701.infra.mapper.OcrDeviceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OcrDeviceRepositoryImpl implements OcrDeviceRepository {

    private final OcrDeviceMapper ocrDeviceMapper;

    public OcrDeviceRepositoryImpl(OcrDeviceMapper ocrDeviceMapper) {
        this.ocrDeviceMapper = ocrDeviceMapper;
    }

    @Override
    public Optional<OcrDevice> findById(Integer id) {
        return Optional.ofNullable(ocrDeviceMapper.selectById(id));
    }

    @Override
    public boolean save(OcrDevice entity) {
        return ocrDeviceMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(OcrDevice entity) {
        return ocrDeviceMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return ocrDeviceMapper.deleteById(id) > 0;
    }

    @Override
    public List<OcrDevice> findAll() {
        return ocrDeviceMapper.selectList(new QueryWrapper<>());
    }
}
