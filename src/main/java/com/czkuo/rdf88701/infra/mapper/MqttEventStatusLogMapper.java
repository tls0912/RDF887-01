package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.MqttEventStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * MQTT事件狀態變更歷程記錄表 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 */
@Mapper
public interface MqttEventStatusLogMapper extends BaseMapper<MqttEventStatusLog> {

}
