package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotInR007;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 入站 R007 MESSAGE 明細（WIP→EQP；一筆對應一個 mqtt_message_log.id） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-15
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface RobotInR007Mapper extends BaseMapper<RobotInR007> {

}
