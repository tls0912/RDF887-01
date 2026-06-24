package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.mission.RobotTaskMonitorService;
import com.czkuo.rdf88701.presentation.web.dto.RobotTaskSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class RobotTaskMonitorController {

    private final RobotTaskMonitorService monitorService;

    /**
     * GET /api/missions/monitor?hours=24&limitPerCmd=200
     */
    @GetMapping("/monitor")
    public MonitorResponse monitor(
            @RequestParam(name = "hours", defaultValue = "24") int hours,
            @RequestParam(name = "limitPerCmd", defaultValue = "200") int limitPerCmd) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        var result = monitorService.loadTasks(since, limitPerCmd);

        return new MonitorResponse(result.current(), result.history());
    }

    public record MonitorResponse(
            List<RobotTaskSummaryDto> current,
            List<RobotTaskSummaryDto> history) {}
}
