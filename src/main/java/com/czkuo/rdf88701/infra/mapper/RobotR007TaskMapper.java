package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * R007 任務主表（Worker 決策 STK_PORT；簡化內部狀態 + 對外結果快取） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-20
 */
@Mapper
public interface RobotR007TaskMapper extends BaseMapper<RobotR007Task> {

}
