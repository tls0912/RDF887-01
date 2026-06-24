package com.czkuo.rdf88701.application.service.report;

import com.czkuo.rdf88701.application.dto.report.Alarm.AlarmQuery;
import com.czkuo.rdf88701.application.dto.report.Alarm.TimelineBucketRow;
import com.czkuo.rdf88701.application.dto.report.Alarm.TopRow;
import com.czkuo.rdf88701.application.dto.report.Alarm.TriggerSpanRow;
import com.czkuo.rdf88701.infra.mapper.AlarmAggMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AlarmReportHybridService
 * ------------------------------------------------------------
 * 混合式報表服務：
 *  - Mapper（DB 聚合）：timeline、top(count)
 *  - Java（配對/彙總）：spans、top(duration)、open、overview
 *
 * 設計要點：
 *  - 時區統一使用 Asia/Taipei（僅用於 now/endCap 的上限計算）
 *  - spans 配對規則：同 global_code 下，TRIGGER 先入佇列，遇 CLEAR 依序配對最早的一筆；
 *    如無 CLEAR，span 以 endCap（min(now, to)）結束。
 *  - 避免一次載入超大量資料：提供 page/size；必要時改用時間游標分段掃描（TODO 標記）。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmReportHybridService {

    private final AlarmAggMapper mapper;                  // timeline / top(count)
    private final NamedParameterJdbcTemplate jdbc;        // 撈原始事件（spans/duration/open/overview）

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // ====== Public DTO（僅本服務用於 /overview 回傳；你也可改放到 application.dto.report.Alarm 套件） ======
    @Data
    public static class AlarmOverview {
        private int currentOpenCount;          // 目前未清除數（以 asOf 計）
        private long todayTriggerCount;        // 今天 TRIGGER 數
        private long todayClearCount;          // 今天 CLEAR 數
        private long mttrSeconds;              // 平均處置時間（僅已清除 spans）
        private long maxOngoingSeconds;        // 最長未清持續秒數（若無開放筆則為 0）
    }

    // ====== 內部用 Row 模型（只存最原始欄位） ======
    @Value
    static class RawEvent {
        int globalCode;
        String eventType;        // TRIGGER / CLEAR
        LocalDateTime createdAt;
        String itemType;         // ALARM / WARNING
        String equipment;        // WIP / ZIPA / ZIPB / ...
        String titleZh;
        String titleEn;
    }

    // =====================================================================
    //  對外方法（對應 Controller 使用）
    // =====================================================================

    /** 時間序列趨勢（Triggers vs Clears） */
    public List<TimelineBucketRow> timeline(AlarmQuery q) {
        String bucket = "day".equalsIgnoreCase(q.getBucket()) ? "day" : "hour";
        return mapper.selectTimeline(q.getFrom(), q.getTo(), normType(q.getType()), q.getEquipments(), bucket);
    }

    /** Top N：metric=count 走 DB；metric=duration 走 Java（spans 累加秒數） */
    public List<TopRow> top(LocalDateTime from, LocalDateTime to, String type, List<String> eqs, String metric, int limit) {
        int lim = Math.max(1, Math.min(limit, 1000)); // 防呆上限
        String t = normType(type);
        if ("duration".equalsIgnoreCase(metric)) {
            List<TriggerSpanRow> spans = spans(from, to, t, eqs, 0, Integer.MAX_VALUE); // 取全量後自行截斷
            Map<Integer, Long> sum = spans.stream()
                    .collect(Collectors.groupingBy(TriggerSpanRow::getGlobalCode,
                            Collectors.summingLong(TriggerSpanRow::getDurationSeconds)));
            // 取任一樣本拿標題
            Map<Integer, RawEvent> any = pickAny(fetchRaw(from, to, t, eqs));

            return sum.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(lim)
                    .map(e -> {
                        RawEvent s = any.get(e.getKey());
                        TopRow r = new TopRow();
                        r.setGlobalCode(e.getKey());
                        r.setTitleZh(s != null ? s.getTitleZh() : "");
                        r.setTitleEn(s != null ? s.getTitleEn() : "");
                        r.setCount(0);
                        r.setTotalDurationSeconds(e.getValue());
                        return r;
                    })
                    .toList();
        }
        return mapper.selectTopByCount(from, to, t, eqs, lim);
    }

    /** Spans 明細（TRIGGER→最近 CLEAR；缺 CLEAR 以 endCap 結束） */
    public List<TriggerSpanRow> spans(LocalDateTime from, LocalDateTime to, String type, List<String> eqs, int page, int size) {
        int sz = Math.max(1, Math.min(size, 2000));
        int off = Math.max(0, page) * sz;

        List<RawEvent> events = fetchRaw(from, to, normType(type), eqs);
        List<TriggerSpanRow> all = buildSpans(events, from, to);
        if (off >= all.size()) return List.of();
        return all.subList(off, Math.min(off + sz, all.size()));
    }

    /** 目前未清清單（以 asOf = min(now, to) 計） */
    public List<TriggerSpanRow> open(LocalDateTime from, LocalDateTime to, String type, List<String> eqs) {
        LocalDateTime asOf = min(LocalDateTime.now(ZONE), to);
        List<RawEvent> events = fetchRaw(from, asOf, normType(type), eqs);
        List<TriggerSpanRow> spans = buildSpans(events, from, asOf);
        // 開放的 span：等效於 clearTime==asOf（因缺 CLEAR 以 endCap 補尾）
        return spans.stream()
                .filter(s -> !s.getClearTime().isBefore(asOf)) // clearTime == asOf
                .sorted(Comparator.comparing(TriggerSpanRow::getDurationSeconds).reversed())
                .toList();
    }

    /** KPI 概覽：currentOpenCount / today triggers/clears / MTTR / max ongoing */
    public AlarmOverview overview(LocalDateTime from, LocalDateTime to, String type, List<String> eqs) {
        LocalDateTime asOf = min(LocalDateTime.now(ZONE), to);
        String t = normType(type);

        // 1) 當前開放
        List<TriggerSpanRow> open = open(from, to, t, eqs);

        // 2) 今日（以 Asia/Taipei，00:00 ~ asOf）
        LocalDateTime todayStart = LocalDate.now(ZONE).atStartOfDay();
        long todayTrig = countEvents(todayStart, asOf, t, eqs, "TRIGGER");
        long todayClr  = countEvents(todayStart, asOf, t, eqs, "CLEAR");

        // 3) MTTR：僅取已清除 spans
        List<TriggerSpanRow> spans = spans(from, asOf, t, eqs, 0, Integer.MAX_VALUE);
        long mttr = avgSeconds(spans.stream()
                .filter(s -> s.getClearTime() != null && s.getClearTime().isBefore(asOf.plusSeconds(1)))
                .mapToLong(TriggerSpanRow::getDurationSeconds)
                .toArray());

        // 4) 最長未清持續
        long maxOngoing = open.stream().mapToLong(TriggerSpanRow::getDurationSeconds).max().orElse(0L);

        AlarmOverview o = new AlarmOverview();
        o.setCurrentOpenCount(open.size());
        o.setTodayTriggerCount(todayTrig);
        o.setTodayClearCount(todayClr);
        o.setMttrSeconds(mttr);
        o.setMaxOngoingSeconds(maxOngoing);
        return o;
    }

    // =====================================================================
    //  Raw 事件讀取（僅 where/order；其他全部在 Java 計算）
    // =====================================================================

    private List<RawEvent> fetchRaw(LocalDateTime from, LocalDateTime to, String type, List<String> eqs) {
        StringBuilder sql = new StringBuilder("""
            SELECT l.global_code, l.event_type, l.created_at,
                   i.type AS item_type, i.equipment, i.title_zh, i.title_en
            FROM alarm_item_log l
            JOIN alarm_item i ON i.global_code = l.global_code
            WHERE l.created_at >= :from AND l.created_at < :to
        """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        if (!"ALL".equalsIgnoreCase(type)) {
            sql.append(" AND i.type = :type ");
            p.addValue("type", type);
        }
        if (eqs != null && !eqs.isEmpty()) {
            sql.append(" AND i.equipment IN (:eqs) ");
            p.addValue("eqs", eqs);
        }
        sql.append(" ORDER BY l.global_code, l.created_at ");

        List<RawEvent> out = new ArrayList<>(4096);
        jdbc.query(sql.toString(), p, new RowCallbackHandler() {
            @Override public void processRow(ResultSet rs) throws SQLException {
                out.add(new RawEvent(
                        rs.getInt("global_code"),
                        rs.getString("event_type"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("item_type"),
                        rs.getString("equipment"),
                        rs.getString("title_zh"),
                        rs.getString("title_en")
                ));
            }
        });
        return out;
    }

    /** 統計某期間的事件次數（TRIGGER/CLEAR） */
    private long countEvents(LocalDateTime from, LocalDateTime to, String type, List<String> eqs, String eventType) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) AS c
            FROM alarm_item_log l
            JOIN alarm_item i ON i.global_code = l.global_code
            WHERE l.created_at >= :from AND l.created_at < :to
              AND l.event_type = :evt
        """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("evt", eventType);
        if (!"ALL".equalsIgnoreCase(type)) {
            sql.append(" AND i.type = :type ");
            p.addValue("type", type);
        }
        if (eqs != null && !eqs.isEmpty()) {
            sql.append(" AND i.equipment IN (:eqs) ");
            p.addValue("eqs", eqs);
        }
        return Objects.requireNonNull(jdbc.queryForObject(sql.toString(), p, Long.class));
    }

    // =====================================================================
    //  配對與彙總（Java 端）
    // =====================================================================

    private List<TriggerSpanRow> buildSpans(List<RawEvent> events, LocalDateTime from, LocalDateTime to) {
        Map<Integer, Deque<RawEvent>> open = new HashMap<>();
        List<TriggerSpanRow> spans = new ArrayList<>();

        LocalDateTime endCap = min(LocalDateTime.now(ZONE), to);

        for (RawEvent ev : events) {
            Deque<RawEvent> q = open.computeIfAbsent(ev.globalCode, k -> new ArrayDeque<>());
            if ("TRIGGER".equals(ev.eventType)) {
                q.addLast(ev);
            } else if ("CLEAR".equals(ev.eventType)) {
                RawEvent t = q.pollFirst();
                if (t != null) spans.add(toSpan(ev.globalCode, t, ev, endCap));
            }
        }
        // 未清除的 TRIGGER 以 endCap 補尾
        for (Map.Entry<Integer, Deque<RawEvent>> e : open.entrySet()) {
            while (!e.getValue().isEmpty()) {
                spans.add(toSpan(e.getKey(), e.getValue().pollFirst(), null, endCap));
            }
        }
        spans.sort(Comparator.comparing(TriggerSpanRow::getTriggerTime));
        return spans;
    }

    private TriggerSpanRow toSpan(int code, RawEvent trig, RawEvent clr, LocalDateTime endCap) {
        LocalDateTime t = trig.createdAt;
        LocalDateTime c = (clr != null) ? clr.createdAt : endCap;
        long sec = Math.max(0, ChronoUnit.SECONDS.between(t, c));
        TriggerSpanRow r = new TriggerSpanRow();
        r.setGlobalCode(code);
        r.setItemType(trig.itemType);
        r.setEquipment(trig.equipment);
        r.setTriggerTime(t);
        r.setClearTime(c);
        r.setDurationSeconds(sec);
        return r;
    }

    private Map<Integer, RawEvent> pickAny(List<RawEvent> events) {
        Map<Integer, RawEvent> m = new HashMap<>();
        for (RawEvent e : events) m.putIfAbsent(e.globalCode, e);
        return m;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private static String normType(String t) {
        if (t == null) return "ALL";
        t = t.toUpperCase(Locale.ROOT);
        return (t.equals("ALARM") || t.equals("WARNING")) ? t : "ALL";
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return (a.isBefore(b) ? a : b);
    }

    private static long avgSeconds(long[] arr) {
        if (arr == null || arr.length == 0) return 0L;
        long sum = 0L;
        for (long v : arr) sum += v;
        return sum / arr.length;
    }
}
