package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.infra.cache.TransferCommandCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.presentation.web.dto.TransferListItemDto;
import com.czkuo.rdf88701.presentation.web.dto.TransferSnapshotDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferCommandCache commandCache;
    private final TransferStatusCache statusCache;

    public TransferController(TransferCommandCache commandCache, TransferStatusCache statusCache) {
        this.commandCache = commandCache;
        this.statusCache = statusCache;
    }

    // GET /api/transfers
    @GetMapping
    public List<TransferListItemDto> list() {
        List<TransferListItemDto> list = new ArrayList<>();
        for (int id = 1; id <= 1; id++) { // 需要多台自己放大範圍
            list.add(new TransferListItemDto(id, nameOf(id)));
        }
        return list;
    }

    // GET /api/transfers/{id}/command/status
    @GetMapping("/{id}/command/status")
    public ResponseEntity<TransferCommandStatus> getCommandStatus(@PathVariable int id) {
        TransferCommandStatus combined = commandCache.getCombined(id);
        if (combined == null) {
            return ResponseEntity.ok(makeEmptyCommand(id));
        }
        return ResponseEntity.ok(combined);
    }

    // GET /api/transfers/{id}/device/status
    @GetMapping("/{id}/device/status")
    public ResponseEntity<TransferDeviceStatus> getDeviceStatus(@PathVariable int id) {
        String name = nameOf(id);
        TransferDeviceStatus latest = statusCache.getLatest(name);
        if (latest == null) {
            return ResponseEntity.ok(makeEmptyStatus(id));
        }
        return ResponseEntity.ok(latest);
    }

    // GET /api/transfers/{id}/snapshot  （一次回 command + status）
    @GetMapping("/{id}/snapshot")
    public ResponseEntity<TransferSnapshotDto> getSnapshot(@PathVariable int id) {
        TransferCommandStatus cmd = commandCache.getLatest(id);
        if (cmd == null) cmd = makeEmptyCommand(id);

        String name = nameOf(id);
        TransferDeviceStatus st = statusCache.getLatest(name);
        if (st == null) st = makeEmptyStatus(id);

        return ResponseEntity.ok(new TransferSnapshotDto(id, cmd, st));
    }

    // helpers
    private TransferCommandStatus makeEmptyCommand(int id) {
        TransferCommandStatus empty = new TransferCommandStatus();
        empty.setTransferId(id);
        empty.setSnapshotTime(Instant.now());
        empty.setStale(true);
        return empty;
    }

    private TransferDeviceStatus makeEmptyStatus(int id) {
        TransferDeviceStatus empty = new TransferDeviceStatus();
        empty.setTransferId(id);
        empty.setSnapshotTime(Instant.now());
        empty.setStale(true);
        return empty;
    }

    private String nameOf(int id) {
        return "Transfer#" + id; // 你的 TransferStatusCache 用 name 當 key
    }
}
