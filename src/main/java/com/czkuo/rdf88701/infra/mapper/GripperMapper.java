package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.Gripper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Gripper 裝置資料存取 Mapper
 * </p>
 *
 * @author czkuo
 * @since 2025-06-30
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface GripperMapper extends BaseMapper<Gripper> {

}
