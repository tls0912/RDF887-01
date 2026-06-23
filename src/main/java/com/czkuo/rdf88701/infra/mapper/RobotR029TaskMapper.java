package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * R029 任務主表：單一流道；同流道同時僅一筆 RUNNING Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Mapper
public interface RobotR029TaskMapper extends BaseMapper<RobotR029Task> {

}
