package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.czkuo.rdf88701.domain.repository.ButtonLogRepository;
import com.czkuo.rdf88701.infra.entity.ButtonLog;
import com.czkuo.rdf88701.infra.mapper.ButtonLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class ButtonLogRepositoryImpl implements ButtonLogRepository {

    private final ButtonLogMapper buttonLogMapper;

    public ButtonLogRepositoryImpl(ButtonLogMapper buttonLogMapper) {
        this.buttonLogMapper = buttonLogMapper;
    }

    @Override
    public Optional<ButtonLog> findById(Long id) {
        return Optional.ofNullable(buttonLogMapper.selectById(id));
    }

    @Override
    public boolean save(ButtonLog entity) {
        return buttonLogMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(ButtonLog entity) {
        return buttonLogMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return buttonLogMapper.deleteById(id) > 0;
    }

    @Override
    public List<ButtonLog> findAll() {
        return buttonLogMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<Integer> findLastSeqIndexByArea(String area) {
        // 取指定 area、依 seq_index desc 排序後的第一筆
        QueryWrapper<ButtonLog> qw = new QueryWrapper<>();
        qw.eq("area", area)
                .orderByDesc("seq_index")
                .last("LIMIT 1");

        ButtonLog row = buttonLogMapper.selectOne(qw);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(row.getSeqIndex());
    }

    @Override
    public boolean existsByAreaAndSeqIndex(String area, int seqIndex) {
        QueryWrapper<ButtonLog> qw = new QueryWrapper<>();
        qw.eq("area", area)
                .eq("seq_index", seqIndex);

        Long count = buttonLogMapper.selectCount(qw);
        return count != null && count > 0;
    }
}
