package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.infra.cache.GripperCommandCache;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.presentation.web.dto.GripperListItemDto;
import com.czkuo.rdf88701.presentation.web.dto.GripperSnapshotDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/grippers")
public class GripperController {

    private final GripperCommandCache cmdCache;
    private final GripperStatusCache stCache;

    public GripperController(GripperCommandCache cmdCache, GripperStatusCache stCache) {
        this.cmdCache = cmdCache;
        this.stCache  = stCache;
    }

    // 固定 8 台
    @GetMapping
    public List<GripperListItemDto> list() {
        List<GripperListItemDto> list = new ArrayList<>();
        for (int id = 1; id <= 8; id++) {
            list.add(new GripperListItemDto(id, nameOf(id)));
        }
        return list;
    }

    @GetMapping("/{id}/command/status")
    public ResponseEntity<GripperCommandStatus> getCommand(@PathVariable int id) {
        var combined = cmdCache.getCombined(id);
        if (combined == null) {
            var empty = new GripperCommandStatus();
            empty.setGripperId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(combined);
    }

    @GetMapping("/{id}/device/status")
    public ResponseEntity<GripperDeviceStatus> getStatus(@PathVariable int id) {
        var latest = stCache.getLatest(nameOf(id)); // 注意：status cache 以名稱為 key
        if (latest == null) {
            var empty = new GripperDeviceStatus();
            empty.setGripperId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<GripperSnapshotDto> getSnapshot(@PathVariable int id) {
        var cmd = cmdCache.getLatest(id);
        if (cmd == null) {
            cmd = new GripperCommandStatus();
            cmd.setGripperId(id);
            cmd.setSnapshotTime(Instant.now());
            cmd.setStale(true);
        }

        var st = stCache.getLatest(nameOf(id));
        if (st == null) {
            st = new GripperDeviceStatus();
            st.setGripperId(id);
            st.setSnapshotTime(Instant.now());
            st.setStale(true);
        }

        return ResponseEntity.ok(new GripperSnapshotDto(id, cmd, st));
    }

    private String nameOf(int id) {
        return "Gripper#" + id;
    }
}
