package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotInR008;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 入站 R008 MESSAGE 明細（EQP→WIP；一筆對應一個 mqtt_message_log.id） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Mapper
public interface RobotInR008Mapper extends BaseMapper<RobotInR008> {

}
