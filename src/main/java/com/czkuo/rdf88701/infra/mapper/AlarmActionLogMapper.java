package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.AlarmActionLog;
import com.czkuo.rdf88701.infra.entity.OcrManualLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-11-18
 */
@Mapper
public interface AlarmActionLogMapper extends BaseMapper<AlarmActionLog> {

}
