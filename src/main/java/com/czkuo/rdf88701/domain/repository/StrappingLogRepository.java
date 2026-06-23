package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.StrappingLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StrappingLogRepository {

    Optional<StrappingLog> findById(Long id);

    boolean save(StrappingLog entity);

    boolean update(StrappingLog entity);

    boolean deleteById(Long id);

    List<StrappingLog> findAll();

    /**
     * 是否已存在（對應 DB uk_pos_epoch_idx）
     */
    boolean existsByMachinePosEpochAndSeqIndex(Byte machinePos, Integer seqEpoch, Integer seqIndex);
    /**
     * 是否已存在（對應 DB uk_pos_epoch_idx）
     */
    boolean existsByMachinePosEpochAndEventTime(Byte machinePos, Integer seqEpoch, LocalDateTime eventTime);

    /**
     * 取得最後一筆寫入 DB 的游標（epoch + index）
     * 用於 monitor 啟動時初始化（避免只拿 seqIndex 因為會重滾）
     */
    Optional<SeqCursor> findLastCursor();

    /**
     * 依時間區間查詢紀錄（含排序，全機台）
     */
    List<StrappingLog> findByTimeRange(LocalDateTime start, LocalDateTime end);

    /**
     * 依時間區間 + 指定機台查詢紀錄（含排序）
     */
    List<StrappingLog> findByTimeRangeAndMachine(LocalDateTime start, LocalDateTime end, Byte machinePos);

    /**
     * 最小 DTO：最後游標
     */
    record SeqCursor(int seqEpoch, int seqIndex) {}
}
