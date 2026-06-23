package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.DoorAccessInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 安全門開/關檢核資訊 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-25
 */
@Mapper
public interface DoorAccessInfoMapper extends BaseMapper<DoorAccessInfo> {

}
