package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.ImageAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 影像外部儲存索引表（S072/S073 等影像引用） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-28
 */
@Mapper
public interface ImageAssetMapper extends BaseMapper<ImageAsset> {

}
