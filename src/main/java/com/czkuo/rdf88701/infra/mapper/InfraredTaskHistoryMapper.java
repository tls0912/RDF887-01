package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.InfraredTaskHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Infrared 任務歷史記錄 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface InfraredTaskHistoryMapper extends BaseMapper<InfraredTaskHistory> {

}
