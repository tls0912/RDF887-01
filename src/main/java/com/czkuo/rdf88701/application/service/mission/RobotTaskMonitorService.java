package com.czkuo.rdf88701.application.service.mission;

import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.presentation.web.dto.RobotTaskSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotTaskMonitorService {

    private final RobotR007TaskRepository robotR007TaskRepository;
    private final RobotR008TaskRepository robotR008TaskRepository;
    private final RobotR029TaskRepository robotR029TaskRepository;
    private final RobotR031TaskRepository robotR031TaskRepository;

    private final RobotInR029LotRepository robotInR029LotRepository;

    /** 回傳給 WPF 用的統一格式 */
    @Transactional(readOnly = true)
    public MonitorResult loadTasks(LocalDateTime historySince, int historyLimitPerCmd) {
        List<RobotTaskSummaryDto> current = new ArrayList<>();
        List<RobotTaskSummaryDto> history = new ArrayList<>();

        // ===== R007 =====
        for (RobotR007Task t : robotR007TaskRepository.findOpen()) {
            current.add(mapR007(t));
        }
        for (RobotR007Task t : robotR007TaskRepository
                .findRecentSince(historySince, historyLimitPerCmd)) {
            if (!isOpen(t.getExternalLastResult())) {
                history.add(mapR007(t));
            }
        }

        // ===== R008 =====
        for (RobotR008Task t : robotR008TaskRepository.findOpen()) {
            current.add(mapR008(t));
        }
        for (RobotR008Task t : robotR008TaskRepository
                .findRecentSince(historySince, historyLimitPerCmd)) {
            if (!isOpen(t.getExternalLastResult())) {
                history.add(mapR008(t));
            }
        }

        // ===== R029 =====
        for (RobotR029Task t : robotR029TaskRepository.findOpen()) {
            current.add(mapR029(t, true));
        }
        for (RobotR029Task t : robotR029TaskRepository
                .findRecentSince(historySince, historyLimitPerCmd)) {
            if (!isOpen(t.getExternalLastResult())) {
                history.add(mapR029(t, false));
            }
        }

        // ===== R031 =====
        for (RobotR031Task t : robotR031TaskRepository.findOpen()) {
            current.add(mapR031(t));
        }
        for (RobotR031Task t : robotR031TaskRepository
                .findRecentSince(historySince, historyLimitPerCmd)) {
            if (!isOpen(t.getExternalLastResult())) {
                history.add(mapR031(t));
            }
        }

        // 排序（這裡用 createdTime DESC，若沒有就用 id DESC）
        Comparator<RobotTaskSummaryDto> cmp =
                Comparator.comparing(RobotTaskSummaryDto::getCreatedTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed();

        current = current.stream().sorted(cmp).collect(toList());
        history = history.stream().sorted(cmp).collect(toList());

        return new MonitorResult(current, history);
    }

    // ====== mapping 區 ======

    private RobotTaskSummaryDto mapR007(RobotR007Task t) {
        RobotTaskSummaryDto dto = base(t.getId(), "R007", t.getTid(),
                t.getLotId(), t.getCarrierId(),
                t.getInternalState(), t.getExternalLastResult(),
                t.getExternalLastTime(), t.getCreatedTime(),
                t.getFailReason(), t.getCancelReason());

        dto.setFromLoc(nz(t.getWipName()));      // 來源 WIP/STK
        dto.setToLoc(nz(t.getDestLoc()));        // 目的 EQP
        dto.setEqpPort(nz(t.getEqpPort()));
        dto.setTrayType(nz(t.getTrayType()));
        return dto;
    }

    private RobotTaskSummaryDto mapR008(RobotR008Task t) {
        RobotTaskSummaryDto dto = base(t.getId(), "R008", t.getTid(),
                t.getLotId(), t.getCarrierId(),
                t.getInternalState(), t.getExternalLastResult(),
                t.getExternalLastTime(), t.getCreatedTime(),
                t.getFailReason(), t.getCancelReason());

        // R008: DEST_LOC = 來源機台, WIPNAME = 目標儲位
        dto.setFromLoc(nz(t.getDestLoc()));      // From EQP
        dto.setToLoc(nz(t.getWipName()));        // To WIP/STK
        dto.setEqpPort(nz(t.getEqpPort()));
        dto.setTrayType(nz(t.getTrayType()));
        return dto;
    }

    private RobotTaskSummaryDto mapR029(RobotR029Task t, boolean includeCarrier) {
        RobotTaskSummaryDto dto = base(t.getId(), "R029", t.getTid(),
                null, null,
                t.getInternalState(), t.getExternalLastResult(),
                t.getExternalLastTime(), t.getCreatedTime(),
                t.getFailReason(), null);

        dto.setFromLoc("STK");
        dto.setToLoc(nz(t.getLane()));          // lane 當成 To
        dto.setEqpPort("");                     // 沒有就留空
        dto.setTrayType(nz(t.getTrayType()));

        if (includeCarrier) {
            Long logId = t.getLogId();
            if (logId != null) {
                var carriers = robotInR029LotRepository.findCarrierIdsByLogId(logId);
                if (!carriers.isEmpty()) {
                    dto.setCarrierId(carriers.get(0)); // 取第一顆代表
                }
            }
        }
        return dto;
    }

    private RobotTaskSummaryDto mapR031(RobotR031Task t) {
        RobotTaskSummaryDto dto = base(t.getId(), "R031", t.getTid(),
                t.getLotId(), t.getCarrierId(),
                t.getInternalState(), t.getExternalLastResult(),
                t.getExternalLastTime(), t.getCreatedTime(),
                null, null);

        dto.setFromLoc(nz(t.getWipName()));      // 來源儲格
        dto.setToLoc(nz(t.getManualPort()));     // 實際放置 Manual Port
        dto.setEqpPort("");
        dto.setTrayType("");
        return dto;
    }

    private RobotTaskSummaryDto base(
            Long id,
            String cmd,
            String tid,
            String lotId,
            String carrierId,
            String internalState,
            String externalLastResult,
            LocalDateTime externalLastTime,
            LocalDateTime createdTime,
            String failReason,
            String cancelReason) {

        RobotTaskSummaryDto dto = new RobotTaskSummaryDto();
        dto.setId(id);
        dto.setCmd(cmd);
        dto.setTid(nz(tid));
        dto.setLotId(nz(lotId));
        dto.setCarrierId(nz(carrierId));
        dto.setInternalState(nz(internalState));
        dto.setExternalLastResult(nz(externalLastResult));
        dto.setExternalLastTime(externalLastTime);
        dto.setCreatedTime(createdTime);
        dto.setFailReason(nz(failReason));
        dto.setCancelReason(nz(cancelReason));
        return dto;
    }

    private boolean isOpen(String externalLastResult) {
        String r = nz(externalLastResult).toUpperCase();
        if (r.isBlank()) return true;
        // 依你實際的 RESULT 代碼調整：
        return switch (r) {
            case "END", "DONE", "SUCCESS", "FAIL", "NG", "CANCEL" -> false;
            default -> true;
        };
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // 封裝 current + history，給 controller 用
    public record MonitorResult(
            List<RobotTaskSummaryDto> current,
            List<RobotTaskSummaryDto> history) {}
}
