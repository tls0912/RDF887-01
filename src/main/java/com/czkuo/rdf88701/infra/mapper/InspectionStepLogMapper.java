package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.InspectionStepLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 異物檢步驟追蹤 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Mapper
public interface InspectionStepLogMapper extends BaseMapper<InspectionStepLog> {

}
