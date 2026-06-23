package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {

    /**
     * 根據使用者名稱查詢單筆資料
     *
     * @param username 使用者名稱
     * @return 使用者資訊
     */
    Users selectByUsername(@Param("username") String username);
}
