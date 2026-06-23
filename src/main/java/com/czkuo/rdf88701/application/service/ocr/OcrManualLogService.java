package com.czkuo.rdf88701.application.service.ocr;


import com.czkuo.rdf88701.application.dto.report.ocr.OcrManualLogRow;
import com.czkuo.rdf88701.infra.mapper.OcrManualLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * AlarmReportHybridService
 * ------------------------------------------------------------
 * 混合式報表服務：
 * - Mapper（DB 聚合）：timeline、top(count)
 * - Java（配對/彙總）：spans、top(duration)、open、overview
 * <p>
 * 設計要點：
 * - 時區統一使用 Asia/Taipei（僅用於 now/endCap 的上限計算）
 * - spans 配對規則：同 global_code 下，TRIGGER 先入佇列，遇 CLEAR 依序配對最早的一筆；
 * 如無 CLEAR，span 以 endCap（min(now, to)）結束。
 * - 避免一次載入超大量資料：提供 page/size；必要時改用時間游標分段掃描（TODO 標記）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrManualLogService {

    private final OcrManualLogMapper mapper;                  // timeline / top(count)
    private final NamedParameterJdbcTemplate jdbc;        // 撈原始事件（spans/duration/open/overview）

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // =====================================================================
    //  對外方法（對應 Controller 使用）
    // =====================================================================


    /**
     * Spans 明細（TRIGGER→最近 CLEAR；缺 CLEAR 以 endCap 結束）
     */
    public List<OcrManualLogRow> spans(LocalDateTime from, LocalDateTime to) {
        return fetchRaw(from, to);
    }


    // =====================================================================
    //  Raw 事件讀取（僅 where/order；其他全部在 Java 計算）
    // =====================================================================

    private List<OcrManualLogRow> fetchRaw(LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("""
                    SELECT 
                    CASE WHEN B.alias_code IS NULL THEN A.`container_main_id` ELSE B.alias_code END AS 'CarrierId'
                    ,A.`curr_ocr_text1`
                    ,A.`curr_ocr_text2`
                    ,A.`ref_site`
                    ,CASE WHEN C.alias_code IS NULL THEN A.`ref_container_id` ELSE C.alias_code END AS 'RefCarrierId'
                    ,A.`ref_ocr_text1`
                    ,A.`ref_ocr_text2`
                    ,A.`manual_decision`
                    ,A.`manual_by`
                    ,A.`manual_time`
                    FROM `rdf887_01`.`ocr_manual_log` A
                    LEFT JOIN `rdf887_01`.`container_main` B ON A.container_main_id=B.id
                    LEFT JOIN `rdf887_01`.`container_main` C ON A.ref_container_id=C.id
                    WHERE A.manual_time >= :from AND A.manual_time < :to
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        sql.append(" ORDER BY A.`manual_time` DESC LIMIT 1000 ");

        List<OcrManualLogRow> out = new ArrayList<>(4096);
        jdbc.query(sql.toString(), p, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                out.add(new OcrManualLogRow(
                        rs.getString("CarrierId"),
                        rs.getString("curr_ocr_text1"),
                        rs.getString("curr_ocr_text2"),
                        rs.getString("ref_site"),
                        rs.getString("RefCarrierId"),
                        rs.getString("ref_ocr_text1"),
                        rs.getString("ref_ocr_text2"),
                        rs.getString("manual_decision"),
                        rs.getString("manual_by"),
                        rs.getTimestamp("manual_time").toLocalDateTime()
                ));
            }
        });
        return out;
    }


    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return (a.isBefore(b) ? a : b);
    }

}
