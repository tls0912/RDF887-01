package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.CameraDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 相機裝置主檔 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 */
@Mapper
public interface CameraDeviceMapper extends BaseMapper<CameraDevice> {

}
