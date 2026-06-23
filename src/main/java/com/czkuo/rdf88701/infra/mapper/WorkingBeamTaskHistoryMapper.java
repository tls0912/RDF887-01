package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTaskHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * WorkingBeam 任務歷史記錄 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 */
@Mapper
public interface WorkingBeamTaskHistoryMapper extends BaseMapper<WorkingBeamTaskHistory> {

}
