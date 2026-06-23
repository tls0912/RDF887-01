package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.infra.cache.WorkingBeamCommandCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.presentation.web.dto.WorkingBeamListItemDto;
import com.czkuo.rdf88701.presentation.web.dto.WorkingBeamSnapshotDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/working-beams")
public class WorkingBeamController {

    private final WorkingBeamCommandCache cmdCache;
    private final WorkingBeamStatusCache stCache;

    public WorkingBeamController(WorkingBeamCommandCache cmdCache, WorkingBeamStatusCache stCache) {
        this.cmdCache = cmdCache;
        this.stCache  = stCache;
    }

    // 固定 1 台（需要多台就把 1 改成 N）
    @GetMapping
    public List<WorkingBeamListItemDto> list() {
        List<WorkingBeamListItemDto> list = new ArrayList<>();
        for (int id = 1; id <= 8; id++) {
            list.add(new WorkingBeamListItemDto(id, nameOf(id)));
        }
        return list;
    }

    @GetMapping("/{id}/command/status")
    public ResponseEntity<WorkingBeamCommandStatus> getCommand(@PathVariable int id) {
        var combined = cmdCache.getCombined(id);
        if (combined == null) {
            var empty = new WorkingBeamCommandStatus();
            empty.setWorkingBeamId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(combined);
    }

    @GetMapping("/{id}/device/status")
    public ResponseEntity<WorkingBeamDeviceStatus> getStatus(@PathVariable int id) {
        var latest = stCache.getLatest(nameOf(id)); // ← 這裡用 name 當 key
        if (latest == null) {
            var empty = new WorkingBeamDeviceStatus();
            empty.setWorkingBeamId(id);
            empty.setSnapshotTime(Instant.now());
            empty.setStale(true);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<WorkingBeamSnapshotDto> getSnapshot(@PathVariable int id) {
        var cmd = cmdCache.getLatest(id);
        if (cmd == null) {
            cmd = new WorkingBeamCommandStatus();
            cmd.setWorkingBeamId(id);
            cmd.setSnapshotTime(Instant.now());
            cmd.setStale(true);
        }

        var st = stCache.getLatest(nameOf(id));
        if (st == null) {
            st = new WorkingBeamDeviceStatus();
            st.setWorkingBeamId(id);
            st.setSnapshotTime(Instant.now());
            st.setStale(true);
        }

        return ResponseEntity.ok(new WorkingBeamSnapshotDto(id, cmd, st));
    }

    private String nameOf(int id) {
        return "WorkingBeam#" + id;
    }
}
