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
 */
@Mapper
public interface InfraredTaskHistoryMapper extends BaseMapper<InfraredTaskHistory> {

}
