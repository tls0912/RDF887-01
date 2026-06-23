package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Transfer 任務請求 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-06-21
 */
@Mapper
public interface TransferRequestMapper extends BaseMapper<TransferRequest> {

}
