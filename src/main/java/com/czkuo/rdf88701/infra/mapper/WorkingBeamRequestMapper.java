package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * WorkingBeam 任務請求 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 */
@Mapper
public interface WorkingBeamRequestMapper extends BaseMapper<WorkingBeamRequest> {

}
