package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.Gripper;

import java.util.List;
import java.util.Optional;

/**
 * Gripper Repository 介面
 * - 定義 Gripper 裝置資料存取規格
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface GripperRepository {

    Optional<Gripper> findById(Long id);

    boolean save(Gripper entity);

    boolean update(Gripper entity);

    boolean deleteById(Long id);

    List<Gripper> findAll();
}
