package com.czkuo.rdf88701.application.monitor.History;


import com.czkuo.rdf88701.domain.repository.ClearHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor

public class ClearHistoryMonitor {

    private final ClearHistoryRepository clearHistoryRepository;

    private static final Map<String, String> TableData365d;
    private static final Map<String, String> TableData90d;
    private static final Map<String, String> TableData30d;
    private static final Map<String, String> TableData15d;

    static {
        TableData90d = new HashMap<>();
        TableData90d.put("mqtt_connection_log", "created_time");
        TableData90d.put("mqtt_event_log", "created_time");
        TableData90d.put("mqtt_event_status_log", "change_time");
        TableData90d.put("mqtt_message_log", "created_time");
        TableData90d.put("button_log", "created_time");
        TableData90d.put("location_reservation_history", "archived_time");
        TableData90d.put("safety_event_log", "change_time");
        TableData90d.put("inspection_job", "created_time");
        TableData90d.put("mqtt_inbox", "created_time");
        TableData90d.put("r029_output_item", "created_time");
        TableData90d.put("robot_r007_task", "created_time");
        TableData90d.put("robot_r008_task", "created_time");
        TableData90d.put("robot_r029_task", "created_time");
        TableData90d.put("robot_r031_task", "created_time");
        TableData90d.put("s072_session", "created_at");
        TableData90d.put("start_access_info", "created_at");

    }

    static {
        TableData365d = new HashMap<>();
        TableData365d.put("alarm_action_log", "import_time");
        TableData365d.put("alarm_item_log", "created_at");
        TableData365d.put("door_access_info", "created_at");
        TableData365d.put("ocr_alarm", "created_at");
        TableData365d.put("ocr_verification", "created_time");
        TableData365d.put("strapping_log", "created_time");
        TableData365d.put("strapping_precheck_result", "created_time");
        TableData365d.put("hmi_display_task", "created_at");


    }

    static {
        TableData30d = new HashMap<>();
        TableData30d.put("l005_session", "created_at");
        TableData30d.put("labeling_info", "created_time");
    }

    static {
        TableData15d = new HashMap<>();
        TableData15d.put("container_attr", "created_time");
        TableData15d.put("location_flow", "archived_time");
        TableData15d.put("crane_request", "created_time");
        TableData15d.put("crane_task", "created_time");
        TableData15d.put("gripper_request", "created_time");
        TableData15d.put("gripper_task", "created_time");
        TableData15d.put("infrared_request", "created_time");
        TableData15d.put("infrared_task", "created_time");
        TableData15d.put("transfer_request", "created_time");
        TableData15d.put("transfer_task", "created_time");
        TableData15d.put("working_beam_request", "created_time");
        TableData15d.put("working_beam_task", "created_time");
        TableData15d.put("location_reservation_record", "reserved_time");
        TableData15d.put("ocr_task", "created_at");
        TableData15d.put("container_data", "created_time");
        TableData15d.put("container_main", "created_time");
        TableData15d.put("crane_request_history", "archived_time");
        TableData15d.put("crane_task_history", "archived_time");
        TableData15d.put("gripper_request_history", "archived_time");
        TableData15d.put("gripper_task_history", "archived_time");
        TableData15d.put("infrared_request_history", "archived_time");
        TableData15d.put("infrared_task_history", "archived_time");
        TableData15d.put("transfer_request_history", "archived_time");
        TableData15d.put("transfer_task_history", "archived_time");
        TableData15d.put("working_beam_request_history", "archived_time");
        TableData15d.put("working_beam_task_history", "archived_time");


    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 5_000)
    public void tick() {

        LocalDateTime now = LocalDateTime.now();
        execute(TableData365d, now.minusDays(365), 1000);
        execute(TableData90d, now.minusDays(90), 1000);
        execute(TableData30d, now.minusDays(30), 1000);
        execute(TableData15d, now.minusDays(15), 1000);

        int result = clearHistoryRepository.deleteMqttMessageLogBeforeTimeByIdDesc(now.minusDays(2), 1000);
        if (result > 0)
            log.info("[Clear] {} time < {} cmd_id='S002' id_desc='CHECK_READY' count={}", "mqtt_message_log", now.minusDays(10), result);

    }

    private void execute(Map<String, String> tables, LocalDateTime clearTime, int limit) {
        int result;
        for (Map.Entry<String, String> entry : tables.entrySet()) {
            try {
                result = clearHistoryRepository.deleteTableDataBeforeTime(
                        entry.getKey(), entry.getValue(), clearTime, limit);
                if (result > 0)
                    log.info("[Clear] {} time < {} count={}", entry.getKey(), clearTime, result);
            } catch (Exception e) {
                log.error("[Clear] {} time < {} ", entry.getKey(), clearTime, e);
            }
        }
    }
}

