package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.S072Session;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * S072 拍照與檢查會話表（支援單/雙拍照模式） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-20
 */
@Mapper
public interface S072SessionMapper extends BaseMapper<S072Session> {

}
