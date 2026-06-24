package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.report.tt.TtDeviceSummaryDto;
import com.czkuo.rdf88701.application.dto.report.tt.TtQueryFilterDto;
import com.czkuo.rdf88701.application.dto.report.tt.TtRecordRowDto;
import com.czkuo.rdf88701.infra.entity.TtRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-12-11
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface TtRecordMapper extends BaseMapper<TtRecord> {

    /** summary：先回 avg + count（p95 由 service 用 Java 算） */
    List<TtDeviceSummaryDto> selectDeviceSummary(@Param("f") TtQueryFilterDto f);

    /** records 分頁：每列含 cycleSec */
    IPage<TtRecordRowDto> selectRecordPage(Page<TtRecordRowDto> page, @Param("f") TtQueryFilterDto f);

    List<BigDecimal> selectCycleListForDevice(@Param("f") TtQueryFilterDto f);

    /** records 分頁：每列含 cycleSec */
    IPage<TtRecordRowDto> selectRecordPageGroupID(Page<TtRecordRowDto> page, @Param("f") TtQueryFilterDto f);
    List<TtRecordRowDto> selectExportData(@Param("f") TtQueryFilterDto f);
}
