package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.RobotInR031;
import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface RobotInR031Repository {

    Optional<RobotInR031> findById(Long id);

    boolean save(RobotInR031 entity);

    boolean update(RobotInR031 entity);

    boolean deleteById(Long id);

    List<RobotInR031> findAll();
}
