package com.czkuo.rdf88701.application.assembler;

import com.czkuo.rdf88701.application.service.query.ContainerQueryService;
import com.czkuo.rdf88701.application.service.query.LocationQueryService;
import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcGripperWordCommand;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GripperWordCommandAssembler
 * - 專責將 GripperTask 組裝為 PlcGripperWordCommand 結構，對應 PLC Word 命令格式
 * - 支援三種指令類型：MOVE / PICK / DROP
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
@RequiredArgsConstructor
public class GripperWordCommandAssembler {

    private final ContainerQueryService containerQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 組裝 PlcGripperWordCommand（對應 PLC Word 命令欄位）
     */
    public PlcGripperWordCommand assemble(GripperTask task) {
        ContainerMain container = task.getContainerMainId() != null ? containerQueryService.getMainById(task.getContainerMainId()) : null;
        LocationPoint location = switch (task.getTaskType()) {
            case "PICK" -> locationQueryService.getById(task.getFromLocationId());
            case "DROP", "MOVE" -> locationQueryService.getById(task.getToLocationId());
            default -> throw new IllegalArgumentException("Unsupported task type: " + task.getTaskType());
        };

        BigDecimal x100 = task.getTargetHeightMm().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);

        return PlcGripperWordCommand.builder()
                .transferNo(task.getId().intValue())                             // 指令編號
                .commandType(parseCommandType(task.getTaskType()))               // 指令類型編碼
                .trayQuantity(task.getLayerCount())                              // 產品層數
                .trayHeight(x100.intValue())                                     // 產品厚度
                .locationLevel(parseLocationCode(location))                      // 位置代碼
                .productId(container != null ? container.getAliasCode() : null)  // 產品序號（或容器條碼）
                .build();
    }

    /**
     * 轉換任務類型為 PLC 對應的指令代碼
     */
    private int parseCommandType(String taskType) {
        return switch (taskType) {
            case "MOVE" -> 1;
            case "PICK" -> 2;
            case "DROP" -> 3;
            default -> throw new IllegalArgumentException("Unknown task type: " + taskType);
        };
    }

    /**
     * 從 Location 解析位置代碼（如可轉為 int，可選擇使用 code 或 level）
     */
    private int parseLocationCode(LocationPoint location) {
        try {
            return Integer.parseInt(location.getCode());
        } catch (NumberFormatException e) {
            return defaultIfNull(location.getLevel());
        }
    }

    private int defaultIfNull(Integer value) {
        return value != null ? value : 0;
    }
}
