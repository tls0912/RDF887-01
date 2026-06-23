package com.czkuo.rdf88701.application.service.report;


import com.czkuo.rdf88701.application.dto.report.Alarm.AlarmActionLogRow;
import com.czkuo.rdf88701.domain.repository.AlarmActionLogRepository;
import com.czkuo.rdf88701.infra.entity.AlarmActionLog;
import com.czkuo.rdf88701.infra.mapper.AlarmActionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
public class AlarmActionLogService {

    private final AlarmActionLogMapper mapper;                  // timeline / top(count)
    private final NamedParameterJdbcTemplate jdbc;        // 撈原始事件（spans/duration/open/overview）
    private final AlarmActionLogRepository alarmActionLogRepository;
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // =====================================================================
    //  對外方法（對應 Controller 使用）
    // ====================================================================


    @Transactional
    public Integer importLogs(List<AlarmActionLogRow> rows) {

        List<AlarmActionLog> entities = rows.stream()
                .map(this::toEntity)
                .toList();

        for (AlarmActionLogRow row : rows) {
            AlarmActionLog entity = toEntity(row);
            alarmActionLogRepository.save(entity);
        }

        return rows.size();
    }
    private AlarmActionLog toEntity(AlarmActionLogRow dto) {
        AlarmActionLog entity = new AlarmActionLog();
        entity.setGlobalCode(dto.getGlobalCode());
        entity.setActionNote(dto.getActionNote());
        entity.setAseCheck(dto.getAseCheck());
        entity.setImportTime(dto.getImportTime());

        return entity;
    }
    /**
     * Spans 明細（TRIGGER→最近 CLEAR；缺 CLEAR 以 endCap 結束）
     */
    public List<AlarmActionLogRow> spans(LocalDateTime from, LocalDateTime to) {
        return fetchRaw(from, to);
    }

    public List<AlarmActionLogRow> spansGroup(LocalDateTime from, LocalDateTime to) {
        return fetchRawGroup(from, to);
    }


    private List<AlarmActionLogRow> fetchRaw(LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                A.global_code AS GlobalCode,
                B.type AS ItemType,
                F.replacement AS Equipment,
                A.title_zh AS TitleZh,
                A.created_at AS TriggerTime,
                '' AS ActionNote, 
                '' AS AseCheck, 
                '' AS ImportTime, 
                0 AS AllCnt, 
                0 AS Cnt 
                FROM alarm_item_log A
                LEFT JOIN alarm_item B 
                    ON A.global_code=B.global_code 
                LEFT JOIN `rename` F 
                    ON B.equipment=F.target 
                WHERE event_type='TRIGGER'
                    AND created_at>=:from 
                    AND created_at<:to 
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        sql.append(" ORDER BY TriggerTime ASC,GlobalCode ASC LIMIT 1000 ");

        List<AlarmActionLogRow> out = new ArrayList<>(4096);
        jdbc.query(sql.toString(), p, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                out.add(new AlarmActionLogRow(
                        rs.getString("TriggerTime"),
                        rs.getLong("GlobalCode"),
                        rs.getString("ItemType"),
                        rs.getString("Equipment"),
                        rs.getString("TitleZh"),
                        rs.getString("ActionNote"),
                        rs.getString("AseCheck"),
                        rs.getString("ImportTime"),
                        rs.getLong("AllCnt"),
                        rs.getLong("Cnt")
                ));
            }
        });
        return out;
    }
    // =====================================================================
    //  Raw 事件讀取（僅 where/order；其他全部在 Java 計算）
    // =====================================================================

    private List<AlarmActionLogRow> fetchRawGroup(LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                A.global_code AS GlobalCode,
                B.type AS ItemType,
                F.replacement AS Equipment,
                A.title_zh AS TitleZh,
                C.import_time AS ImportTime,
                E.AllCnt,
                A.Cnt,
                D.action_note AS ActionNote,
                D.ase_check AS AseCheck,
                A.CreatedTime AS TriggerTime 
                FROM
                (
                    SELECT 
                        global_code,
                        title_zh,
                        substr(created_at,1,10) AS CreatedTime,
                        COUNT(*) AS Cnt 
                    FROM rdf887_01.alarm_item_log 
                    WHERE event_type='TRIGGER'
                      AND created_at>=:from 
                      AND created_at<:to 
                    GROUP BY global_code,CreatedTime,title_zh 
                ) A 
                LEFT JOIN alarm_item B 
                    ON A.global_code=B.global_code 
                LEFT JOIN
                (
                    SELECT global_code, MAX(import_time) AS import_time 
                    FROM rdf887_01.alarm_action_log 
                    GROUP BY global_code 
                ) C 
                    ON A.global_code=C.global_code 
                LEFT JOIN alarm_action_log D 
                    ON C.global_code=D.global_code 
                   AND C.import_time=D.import_time 
                LEFT JOIN
                (
                    SELECT 
                        global_code,
                        title_zh,
                        COUNT(*) AS AllCnt 
                    FROM rdf887_01.alarm_item_log 
                    WHERE event_type='TRIGGER' 
                        AND created_at>=:from 
                        AND created_at<:to 
                    GROUP BY global_code,title_zh 
                ) E 
                   ON A.global_code=E.global_code 
                LEFT JOIN `rename` F 
                    ON B.equipment=F.target 
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        sql.append(" ORDER BY TriggerTime ASC,AllCnt DESC,GlobalCode ASC LIMIT 1000 ");

        List<AlarmActionLogRow> out = new ArrayList<>(4096);
        jdbc.query(sql.toString(), p, new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                out.add(new AlarmActionLogRow(
                        rs.getString("TriggerTime"),
                        rs.getLong("GlobalCode"),
                        rs.getString("ItemType"),
                        rs.getString("Equipment"),
                        rs.getString("TitleZh"),
                        rs.getString("ActionNote"),
                        rs.getString("AseCheck"),
                        rs.getString("ImportTime"),
                        rs.getLong("AllCnt"),
                        rs.getLong("Cnt")
                ));
            }
        });
        return out;
    }


    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return (a.isBefore(b) ? a : b);
    }

}
