package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * HMI 訊息（中/英） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-25
 */
@Mapper
public interface HmiDisplayTaskMapper extends BaseMapper<HmiDisplayTask> {

}
