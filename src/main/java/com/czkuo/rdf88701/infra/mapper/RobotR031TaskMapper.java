package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotR031Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * R031 任務單（WIP/STK → Manual Port 任務追蹤） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Mapper
public interface RobotR031TaskMapper extends BaseMapper<RobotR031Task> {

}
