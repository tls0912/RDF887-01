package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.infra.cache.CraneCommandCache;
import com.czkuo.rdf88701.infra.cache.CraneStatusCache;
import com.czkuo.rdf88701.presentation.web.dto.CraneListItemDto;
import com.czkuo.rdf88701.presentation.web.dto.CraneSnapshotDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/cranes")
public class CraneController {

    private final CraneCommandCache commandCache;
    private final CraneStatusCache statusCache;

    public CraneController(CraneCommandCache commandCache, CraneStatusCache statusCache) {
        this.commandCache = commandCache;
        this.statusCache = statusCache;
    }

    // === 你 WPF 的 HttpCraneService.GetCranesAsync 會打這支 ===
    @GetMapping
    public List<CraneListItemDto> list() {
        // 這裡先簡單：你說 Crane 可能只有 1 台；若要多台改 range 即可。
        // 之後可改讀設定或 DB。
        List<CraneListItemDto> list = new ArrayList<>();
        for (int id = 1; id <= 1; id++) {  // ← 需要多台改這裡，例如 <= 9
            list.add(new CraneListItemDto(id, nameOf(id)));
        }
        return list;
    }

    // === Command 狀態：WPF 綁定 CraneCommandStatusDto; 直接回 domain，欄位名一致 ===
    @GetMapping("/{id}/command/status")
    public ResponseEntity<CraneCommandStatus> getCommandStatus(@PathVariable int id) {
        CraneCommandStatus combined = commandCache.getCombined(id);
        if (combined == null) {
            // 回一筆「stale=true」的空資料，WPF 綁定不會炸
            CraneCommandStatus empty = new CraneCommandStatus();
            empty.setCraneId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(combined);
    }

    // === Device 狀態：WPF 綁定 CraneDeviceStatusDto; 直接回 domain，欄位名一致 ===
    @GetMapping("/{id}/device/status")
    public ResponseEntity<CraneDeviceStatus> getDeviceStatus(@PathVariable int id) {
        String craneName = nameOf(id); // 你的 CraneStatusCache 用 name 當 key
        CraneDeviceStatus latest = statusCache.getLatest(craneName);
        if (latest == null) {
            CraneDeviceStatus empty = new CraneDeviceStatus();
            empty.setCraneId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<CraneSnapshotDto> getSnapshot(@PathVariable int id) {
        // command：從 cache 合併（read + lastWrite）
        CraneCommandStatus cmd = commandCache.getLatest(id);
        if (cmd == null) cmd = makeEmptyCommand(id);

        // status：你的 StatusCache 用 name 當 key
        String craneName = nameOf(id);
        CraneDeviceStatus st = statusCache.getLatest(craneName);
        if (st == null) st = makeEmptyStatus(id);

        return ResponseEntity.ok(new CraneSnapshotDto(id, cmd, st));
    }

    // ====== helpers ======
    private CraneCommandStatus makeEmptyCommand(int id) {
        CraneCommandStatus empty = new CraneCommandStatus();
        empty.setCraneId(id);
        empty.setSnapshotTime(Instant.now());
        empty.setStale(true);
        return empty;
    }

    private CraneDeviceStatus makeEmptyStatus(int id) {
        CraneDeviceStatus empty = new CraneDeviceStatus();
        empty.setCraneId(id);
        empty.setSnapshotTime(Instant.now());
        empty.setStale(true);
        return empty;
    }


    // 目前你的 StatusCache 以 name 為 key；先用預設命名規則
    private String nameOf(int id) {
        return "Crane#" + id;
    }
}
