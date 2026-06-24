package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.InspectionStation;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 異物檢虛擬站（含拍照順序與綁定相機/夾爪） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-09-10
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface InspectionStationMapper extends BaseMapper<InspectionStation> {

}
