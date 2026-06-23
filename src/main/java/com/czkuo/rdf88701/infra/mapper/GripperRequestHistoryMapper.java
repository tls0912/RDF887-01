package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.GripperRequestHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Mapper
public interface GripperRequestHistoryMapper extends BaseMapper<GripperRequestHistory> {

    int batchInsert(@Param("list") List<GripperRequestHistory> list);
}
