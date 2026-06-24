package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.application.dto.query.UsersQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.Users;

import java.util.List;
import java.util.Optional;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public interface UsersRepository {

    Optional<Users> findById(Long id);

    Optional<Users> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 多筆查詢（不分頁）
     */
    List<Users> findByCondition(UsersQuery query);

    /**
     * 分頁查詢
     */
    PageResult<Users> findPageByCondition(UsersQuery query);

    boolean save(Users user);

    boolean update(Users user);

    boolean deleteById(Long id);

    List<Users> findAll();
}
