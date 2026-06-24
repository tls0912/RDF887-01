package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.MqttConnectionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * MQTT 連線與斷線事件歷程表（可用於日誌、統計、通知） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-05
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface MqttConnectionLogMapper extends BaseMapper<MqttConnectionLog> {

}
