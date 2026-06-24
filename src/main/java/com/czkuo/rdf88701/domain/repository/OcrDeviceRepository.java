package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.OcrDevice;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface OcrDeviceRepository {

    Optional<OcrDevice> findById(Integer id);

    boolean save(OcrDevice entity);

    boolean update(OcrDevice entity);

    boolean deleteById(Integer id);

    List<OcrDevice> findAll();
}
