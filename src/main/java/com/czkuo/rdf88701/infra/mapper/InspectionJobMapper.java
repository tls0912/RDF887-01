package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.InspectionJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 異物檢工作流主檔（保證每支夾爪同時僅一筆進行中） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Mapper
public interface InspectionJobMapper extends BaseMapper<InspectionJob> {

}
