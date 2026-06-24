package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.TtSignalDef;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface TtSignalDefRepository {

    Optional<TtSignalDef> findById(Long id);

    boolean save(TtSignalDef entity);

    boolean saveBatch(List<TtSignalDef> items);

    boolean update(TtSignalDef entity);

    boolean deleteById(Long id);

    List<TtSignalDef> findAll();

    /** 查某設備所有定義（依 step_no 排序，Monitor 用） */
    List<TtSignalDef> findByDevice(String deviceType, String deviceName);

    /** 查某設備所有定義（依 step_no 排序，Monitor 用） */
    List<TtSignalDef> findByPlcArea(String plcArea);

    /** 依 plc_word 反查（debug 或特殊需求） */
    Optional<TtSignalDef> findByDeviceAndPlcWord(String deviceType, String deviceName, String plcWord);

    /** 檢查是否已存在（避免重複匯入） */
    boolean existsByDeviceAndPlcWord(String deviceType, String deviceName, String plcWord);
}
