package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.czkuo.rdf88701.domain.repository.R029OutputItemRepository;
import com.czkuo.rdf88701.infra.entity.R029OutputItem;
import com.czkuo.rdf88701.infra.mapper.R029OutputItemMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Repository
public class R029OutputItemRepositoryImpl implements R029OutputItemRepository {

    private final R029OutputItemMapper r029OutputItemMapper;

    public R029OutputItemRepositoryImpl(R029OutputItemMapper r029OutputItemMapper) {
        this.r029OutputItemMapper = r029OutputItemMapper;
    }

    @Override
    public Optional<R029OutputItem> findById(Long id) {
        return Optional.ofNullable(r029OutputItemMapper.selectById(id));
    }

    @Override
    public boolean save(R029OutputItem entity) {
        return r029OutputItemMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(R029OutputItem entity) {
        return r029OutputItemMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return r029OutputItemMapper.deleteById(id) > 0;
    }

    @Override
    public List<R029OutputItem> findAll() {
        return r029OutputItemMapper.selectList(new QueryWrapper<>());
    }

    // ====== 新增實作 ======

    @Override
    public Optional<R029OutputItem> findOneByTaskIdAndNewCarrierId(Long taskId, String newCarrierId) {
        if (taskId == null || StringUtils.isBlank(newCarrierId)) return Optional.empty();
        QueryWrapper<R029OutputItem> qw = new QueryWrapper<>();
        qw.eq("task_id", taskId)
                .eq("new_carrier_id", newCarrierId)
                .last("LIMIT 1");
        return Optional.ofNullable(r029OutputItemMapper.selectOne(qw));
    }

    @Override
    public List<R029OutputItem> findByTaskId(Long taskId) {
        if (taskId == null) return List.of();
        return r029OutputItemMapper.selectList(
                new QueryWrapper<R029OutputItem>().eq("task_id", taskId)
        );
    }

    @Override
    public int countByTaskId(Long taskId) {
        if (taskId == null) return 0;
        return r029OutputItemMapper.selectCount(
                new QueryWrapper<R029OutputItem>().eq("task_id", taskId)
        ).intValue();
    }

    @Override
    public boolean deleteByTaskId(Long taskId) {
        if (taskId == null) return false;
        return r029OutputItemMapper.delete(
                new QueryWrapper<R029OutputItem>().eq("task_id", taskId)
        ) > 0;
    }

    @Override
    public int countByTaskIdAndStates(Long taskId, Collection<String> states) {
        if (taskId == null || states == null || states.isEmpty()) return 0;
        return r029OutputItemMapper.selectCount(
                new QueryWrapper<R029OutputItem>()
                        .eq("task_id", taskId)
                        .in("state", states)
        ).intValue();
    }

    @Override
    public List<R029OutputItem> findByTaskIdAndStateIn(Long taskId, Collection<String> states) {
        if (taskId == null || states == null || states.isEmpty()) return List.of();
        return r029OutputItemMapper.selectList(
                new QueryWrapper<R029OutputItem>()
                        .eq("task_id", taskId)
                        .in("state", states)
        );
    }

    @Override
    public List<R029OutputItem> findByTaskIdAndStateNotIn(Long taskId, Collection<String> states) {
        if (taskId == null) return List.of();
        QueryWrapper<R029OutputItem> qw = new QueryWrapper<R029OutputItem>().eq("task_id", taskId);
        if (states != null && !states.isEmpty()) {
            qw.notIn("state", states);
        }
        return r029OutputItemMapper.selectList(qw);
    }

    @Override
    public boolean updateStateById(Long id, String state) {
        if (id == null) return false;
        UpdateWrapper<R029OutputItem> uw = new UpdateWrapper<>();
        uw.eq("id", id).set("state", state).set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()));
        return r029OutputItemMapper.update(null, uw) > 0;
    }

    @Override
    public boolean updateStateByTaskAndCarrier(Long taskId, String newCarrierId, String state) {
        if (taskId == null || StringUtils.isBlank(newCarrierId)) return false;
        UpdateWrapper<R029OutputItem> uw = new UpdateWrapper<>();
        uw.eq("task_id", taskId)
                .eq("new_carrier_id", newCarrierId)
                .set("state", state)
                .set("updated_time", new java.sql.Timestamp(System.currentTimeMillis()));
        return r029OutputItemMapper.update(null, uw) > 0;
    }

    @Override
    public List<String> findDistinctNewCarrierIdsByTaskId(Long taskId) {
        if (taskId == null) return List.of();
        QueryWrapper<R029OutputItem> qw = new QueryWrapper<>();
        qw.select("DISTINCT new_carrier_id")
                .eq("task_id", taskId);
        // selectObjs 會回傳每列單欄位的值（Object），轉字串即可
        return r029OutputItemMapper.selectObjs(qw).stream()
                .map(obj -> obj == null ? null : obj.toString())
                .filter(StringUtils::isNotBlank)
                .toList();
    }
}
