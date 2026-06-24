package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.R029OutputItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * R029 產出與上架追蹤（逐新載具；狀態欄為 state） Mapper 接口
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
public interface R029OutputItemMapper extends BaseMapper<R029OutputItem> {

}
