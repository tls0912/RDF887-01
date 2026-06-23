package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.StartAccessInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * RESET/START 驗證資訊 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-25
 */
@Mapper
public interface StartAccessInfoMapper extends BaseMapper<StartAccessInfo> {

}
