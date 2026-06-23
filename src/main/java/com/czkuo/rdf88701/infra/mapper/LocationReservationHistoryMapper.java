package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.LocationReservationHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 儲位預約紀錄歷史 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-12
 */
@Mapper
public interface LocationReservationHistoryMapper extends BaseMapper<LocationReservationHistory> {

}
