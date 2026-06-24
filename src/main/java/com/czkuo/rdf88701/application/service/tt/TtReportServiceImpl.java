package com.czkuo.rdf88701.application.service.tt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.report.tt.*;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.repository.TtRecordItemRepository;
import com.czkuo.rdf88701.infra.mapper.TtRecordItemMapper;
import com.czkuo.rdf88701.infra.mapper.TtRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
/**
 * TT 報表查詢服務實作。
 *
 * <p>提供 TT 設備摘要、明細分頁、item 查詢、group id 統計與匯出資料查詢。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Service
@RequiredArgsConstructor
public class TtReportServiceImpl implements TtReportService {

    private final TtRecordMapper recordMapper;
    private final TtRecordItemMapper itemMapper;
    private final TtRecordItemRepository itemRepository;
    private final NamedParameterJdbcTemplate jdbc;        // 撈原始事件（spans/duration/open/overview）

    @Override
    public List<TtDeviceSummaryDto> getSummary(TtQueryFilterDto f) {
        // 先拿 count + avg（已含 cycleSec join）
        List<TtDeviceSummaryDto> base = recordMapper.selectDeviceSummary(f);
        if (base == null || base.isEmpty()) return List.of();

        // 再逐 device 補 p95（保守/穩定版：Java 算）
        for (TtDeviceSummaryDto s : base) {
            List<BigDecimal> cycles = selectCycleListForDevice(f, s.getDeviceType(), s.getDeviceName());
            cycles.sort(Comparator.naturalOrder());
            s.setP95CycleSec(p95NearestRank(cycles));
        }
        return base;
    }

    @Override
    public PageResult<TtRecordRowDto> getRecordsPage(TtQueryFilterDto f, int pageNum, int pageSize) {
        Page<TtRecordRowDto> page = Page.of(pageNum, pageSize);
        IPage<TtRecordRowDto> res = recordMapper.selectRecordPage(page, f);
        return new PageResult<>(pageNum, pageSize, res.getTotal(), res.getRecords());
    }

    @Override
    public List<TtRecordItemRowDto> getItems(long recordId) {
        return Optional.ofNullable(itemMapper.selectItemsByRecordId(recordId)).orElse(List.of());
    }

    // --- helpers ---

    /**
     * 取某 device 的 cycle_sec 清單（用 records page 那段 join 的概念，但只取 cycle_sec 一欄）
     */
    private List<BigDecimal> selectCycleListForDevice(TtQueryFilterDto base, String deviceType, String deviceName) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(base.getFrom());
        f.setTo(base.getTo());
        f.setTransferNo(base.getTransferNo());
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        return Optional.ofNullable(recordMapper.selectCycleListForDevice(f)).orElse(List.of());
    }

    private static BigDecimal p95NearestRank(List<BigDecimal> sortedAsc) {
        int n = sortedAsc.size();
        if (n == 0) return BigDecimal.ZERO;
        int rank = (int) Math.ceil(0.95 * n);
        int idx = Math.min(Math.max(rank - 1, 0), n - 1);
        return sortedAsc.get(idx);
    }

    @Override
    public List<TtRecordRowGroupIdDto> getSummaryGroupID(TtQueryFilterDto f) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                i.remark_id AS remarkId,
                cnt,
                SUM(i.time_sec) AS cycleSec, 
                r.device_area AS deviceArea
                FROM (
                SELECT `remark_id`,count(*) AS cnt
                FROM `rdf887_01`.`tt_record`
                where remark_Id is not null
                AND created_time>=:from
                AND created_time<:to 
                group by `remark_id`
                ) A
                INNER JOIN tt_record r ON A.remark_id=r.remark_id
                LEFT JOIN tt_record_item i ON i.record_id = r.id 
                where i.remark_Id is not null 
                AND created_time>=:from
                AND created_time<:to 
                GROUP BY i.remark_id,r.device_area,cnt 
                ORDER BY cnt DESC
                """);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", f.getFrom())
                .addValue("to", f.getTo());
                //.addValue("cId", f.getRemarkId());
        LocalDateTime dt = LocalDateTime.now();
        List<TtRecordRowGroupIdDto> out = new ArrayList<>(4096);
        jdbc.query(sql.toString(), p, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                out.add(new TtRecordRowGroupIdDto(
                        rs.getString("remarkId"),
                        rs.getInt("cnt"),
                        rs.getBigDecimal("cycleSec"),
                        rs.getString("deviceArea")
                ));
            }
        });
        return out;
        //return base;
    }

    @Override
    public PageResult<TtRecordRowDto> getRecordsPageGroupId(TtQueryFilterDto f, int pageNum, int pageSize) {
        Page<TtRecordRowDto> page = Page.of(pageNum, pageSize);
        IPage<TtRecordRowDto> res = recordMapper.selectRecordPageGroupID(page, f);
        return new PageResult<>(pageNum, pageSize, res.getTotal(), res.getRecords());
    }

    @Override
    public List<TtRecordRowDto> getExportData(TtQueryFilterDto f) {
        return recordMapper.selectExportData(f);
    }
}
