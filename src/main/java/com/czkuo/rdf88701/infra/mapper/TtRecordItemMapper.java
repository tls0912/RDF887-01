package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.application.dto.report.tt.TtRecordItemRowDto;
import com.czkuo.rdf88701.infra.entity.TtRecordItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-12-11
 */
@Mapper
public interface TtRecordItemMapper extends BaseMapper<TtRecordItem> {

    List<TtRecordItemRowDto> selectItemsByRecordId(@Param("recordId") Long recordId);
}
