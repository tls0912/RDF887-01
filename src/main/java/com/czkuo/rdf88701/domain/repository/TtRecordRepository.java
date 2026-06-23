package com.czkuo.rdf88701.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.czkuo.rdf88701.application.monitor.strapping.Strapping1Monitor;
import com.czkuo.rdf88701.infra.entity.TtRecord;
import java.util.List;
import java.util.Optional;

public interface TtRecordRepository {

    Optional<TtRecord> findById(Long id);

    boolean save(TtRecord entity);

    boolean update(TtRecord entity);

    boolean deleteById(Long id);

    List<TtRecord> findAll();

    /** 取某設備最後一筆 tt_index（若無資料回 Optional.empty） */
    Optional<String> findLastIndex(String deviceType, String deviceName);

    /** 防止重複寫入：同設備 + tt_index 是否已存在 */
    boolean existsByDeviceAndIndex(String deviceType, String deviceName, String ttIndex);

    /** 取某設備最新 N 筆（方便除錯/報表） */
    List<TtRecord> findLatestByDevice(String deviceType, String deviceName, int limit);

}
