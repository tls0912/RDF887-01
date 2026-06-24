package com.czkuo.rdf88701.application.assembler;

import com.czkuo.rdf88701.application.service.query.ContainerQueryService;
import com.czkuo.rdf88701.application.service.query.LocationQueryService;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcTransferWordCommand;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * TransferWordCommandAssembler
 * - 專責將 TransferTask 組裝為 PlcTransferWordCommand 結構，對應 PLC Word 命令格式
 * - 支援三種指令類型：MOVE / PICK / DROP
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class TransferWordCommandAssembler {

    private static final long TRANSFER_DEVICE_TR6 = 6L;

    private final ContainerQueryService containerQueryService;
    private final LocationQueryService locationQueryService;

    public PlcTransferWordCommand assemble(TransferTask task) {
        // 容器（可能為 null：如某些 PICK 任務尚未綁定容器）
        ContainerMain container = task.getContainerMainId() != null
                ? containerQueryService.getMainById(task.getContainerMainId())
                : null;

        // 位置：PICK 用 from、DROP/MOVE 用 to
        LocationPoint location = switch (task.getTaskType()) {
            case "PICK" -> locationQueryService.getById(task.getFromLocationId());
            case "DROP", "MOVE" -> locationQueryService.getById(task.getToLocationId());
            default -> throw new IllegalArgumentException("Unsupported task type: " + task.getTaskType());
        };
        if (location == null) {
            throw new IllegalArgumentException("Location not found for taskType=" + task.getTaskType());
        }

        // 依 transfer 裝置是否為 6，決定帶 containerCode 或 aliasCode
        String productId = resolveProductIdForTransfer(container, task.getTransferId());

        return PlcTransferWordCommand.builder()
                .transferNo(safeInt(task.getId()))                 // 任務流水號
                .transferType(parseCommandType(task.getTaskType()))// 1/2/3
                .locationLevel(parseLevel(location.getCode()))     // 位置層級（字串轉 int，失敗給 0）
                .productId(productId)                              // 依裝置 6 or not
                .build();
    }

    private String resolveProductIdForTransfer(ContainerMain container, Long transferId) {
        if (container == null) return null;

        boolean isTR6 = (transferId != null && transferId == TRANSFER_DEVICE_TR6);

        String alias = StringUtils.trimToEmpty(container.getAliasCode());
        String code   = StringUtils.trimToEmpty(container.getContainerCode());

        if (isTR6) {
            // TR6 要帶條碼（containerCode）。若空，保險起見退回 alias（避免下位機拿到空字串）。
            return !code.isEmpty() ? code : alias;
        } else {
            // 其他 transfer 裝置都帶 aliasCode
            return alias;
        }
    }

    private int parseCommandType(String taskType) {
        return switch (taskType) {
            case "MOVE" -> 1;
            case "PICK" -> 2;
            case "DROP" -> 3;
            default -> throw new IllegalArgumentException("Unknown task type: " + taskType);
        };
    }

    private int parseLevel(String levelStr) {
        if (StringUtils.isBlank(levelStr)) return 0;
        try {
            return Integer.parseInt(levelStr.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int safeInt(Long v) {
        if (v == null) return 0;
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("transferNo out of int range: " + v);
        }
        return v.intValue();
    }
}