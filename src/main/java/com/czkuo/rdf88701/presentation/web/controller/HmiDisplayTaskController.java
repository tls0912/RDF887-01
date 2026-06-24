package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.domain.dto.hmi.HmiDisplayEvent;
import com.czkuo.rdf88701.domain.repository.HmiDisplayTaskRepository;
import com.czkuo.rdf88701.infra.entity.HmiDisplayTask;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HMI 顯示任務 REST Controller
 *
 * - /api/hmi/pull   : 以 id 游標做增量拉取（非 PENDING），回傳 id ASC
 * - /api/hmi/latest : 取得最近 N 筆（非 PENDING），回傳 sent_at DESC, id DESC
 * - /api/hmi/current: 取得最新一筆（非 PENDING）
 *
 * 注意：
 * - 若前端在局域網不同來源（WPF 內建 HttpClient 通常沒同源限制），
 *   你也可以在這裡加上 @CrossOrigin 做 CORS 放行。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/hmi")
@RequiredArgsConstructor
public class HmiDisplayTaskController {

    private final HmiDisplayTaskRepository repo;

    /**
     * 增量拉取：id > afterId 且 status != PENDING
     * 依 id ASC 回傳，方便前端按序 append
     *
     * 範例：
     *   GET /api/hmi/pull?afterId=123&limit=100
     */
    @GetMapping("/pull")
    public ResponseEntity<List<HmiDisplayEvent>> pull(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit) {

        int cap = normalizeLimit(limit);
        List<HmiDisplayTask> rows = repo.findSinceId(afterId, cap);
        List<HmiDisplayEvent> out = rows.stream()
                .map(HmiDisplayTaskController::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /**
     * 最近 N 筆（非 PENDING），適合前端初次載入歷史
     *
     * 範例：
     *   GET /api/hmi/latest?limit=50
     */
    @GetMapping("/latest")
    public ResponseEntity<List<HmiDisplayEvent>> latest(
            @RequestParam(defaultValue = "50") int limit) {

        int cap = normalizeLimit(limit);
        List<HmiDisplayTask> rows = repo.findLatestNonPending(cap);
        List<HmiDisplayEvent> out = rows.stream()
                .map(HmiDisplayTaskController::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /**
     * 取最新一筆（非 PENDING）
     *
     * 範例：
     *   GET /api/hmi/current
     */
    @GetMapping("/current")
    public ResponseEntity<HmiDisplayEvent> current() {
        // 直接借用 latest(1)；若 findLatestNonPending 已保證 sent_at DESC, id DESC，這裡就取第 0 筆
        List<HmiDisplayTask> rows = repo.findLatestNonPending(1);
        if (rows.isEmpty()) return ResponseEntity.ok().build();
        return ResponseEntity.ok(toDto(rows.get(0)));
    }

    // ---------------------
    // helpers
    // ---------------------

    private static int normalizeLimit(int limit) {
        // 防止一次拉太多/太少；你也可放到 application.yml 做成可調參數
        if (limit < 1) return 1;
        if (limit > 500) return 500;
        return limit;
    }

    private static HmiDisplayEvent toDto(HmiDisplayTask t) {
        HmiDisplayEvent e = new HmiDisplayEvent();
        e.setId(t.getId());
        e.setTid(nz(t.getTid()));
        e.setMsgEn(nz(t.getMsgEn()));
        e.setMsgCh(nz(t.getMsgCh()));
        e.setStatus(nz(t.getStatus()));
        e.setSentAt(t.getSentAt());
        e.setCreatedAt(t.getCreatedAt());
        // index（若你未存欄位）就先不帶；如果之後加了 index_no 欄位，可在這裡 set 進去
        // e.setIndex(t.getIndexNo());
        return e;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
