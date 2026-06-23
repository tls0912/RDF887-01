package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.SiteBidirRoute;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 雙向站點路徑選擇（告知 walker 出到哪個站點） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 */
@Mapper
public interface SiteBidirRouteMapper extends BaseMapper<SiteBidirRoute> {

}
