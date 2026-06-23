package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.MqttEventLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * MQTT事件可靠推送/補償事件記錄表 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 */
@Mapper
public interface MqttEventLogMapper extends BaseMapper<MqttEventLog> {

}
