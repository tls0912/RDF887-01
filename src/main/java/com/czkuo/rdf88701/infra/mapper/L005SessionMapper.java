package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.L005Session;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * L005 會話：對方檢核結果與我方進度分欄；只註記失效 Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface L005SessionMapper extends BaseMapper<L005Session> {

}
