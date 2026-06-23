package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerAttr;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ContainerAttrRepository {

    // ---- 既有 CRUD ----
    Optional<ContainerAttr> findById(Long id);

    boolean save(ContainerAttr entity);

    boolean update(ContainerAttr entity);

    boolean deleteById(Long id);

    List<ContainerAttr> findAll();

    // ---- 新增便捷方法 ----

    /**
     * 以 (container_main_id, attr_key) 取得單筆屬性
     */
    Optional<ContainerAttr> findOne(Long containerMainId, String attrKey);

    Map<String, ContainerAttr> findContainerAttrs(Long containerMainId);

    /**
     * 單筆 upsert（需要唯一鍵 uk_main_attr(container_main_id, attr_key)）
     */
    boolean upsert(ContainerAttr entity);

    /**
     * 批次 upsert（空清單返回 0）
     */
    int batchUpsert(List<ContainerAttr> list);

    /**
     * 以 (attr_key, attr_value) 反查所有符合列（常用於依 Job 綁定反查容器）
     */
    List<ContainerAttr> findByKeyAndValue(String attrKey, String attrValue);

    /**
     * 依 container_main_id 刪除全部 attr
     */
    boolean deleteByContainerMainId(Long containerMainId);

    /**
     * 依 (container_main_id, attr_key) 刪除單一屬性
     */
    boolean deleteOne(Long containerMainId, String attrKey);

    // ---- 新增（供 R029/占用判斷/釋放用）----

    /**
     * 找出擁有指定 attr_key 的所有 container_main_id（去重）
     */
    List<Long> findContainerIdsByAttrKey(String attrKey);

    /**
     * 找出擁有指定 (attr_key, attr_value) 的 container_main_id（去重）
     */
    List<Long> findContainerIdsByAttrKeyAndValue(String attrKey, String attrValue);

    /**
     * 判斷某 container 是否存在指定 attr_key
     */
    boolean existsByContainerIdAndKey(Long containerMainId, String attrKey);

    /**
     * 依 (attr_key, attr_value) 刪除（例如清 LOG_ID=某筆 R029）
     */
    int deleteByAttrKeyAndValue(String attrKey, String attrValue);

    /**
     * 針對一批 containerIds，刪除多個 keys（例如一次清 LOG_ID/CMD_ID/COUNT/TID）
     */
    int deleteByContainerIdsAndKeys(List<Long> containerMainIds, List<String> keys);
}
