package com.czkuo.rdf88701.application.assembler;

import com.czkuo.rdf88701.infra.adapter.plc.dto.PlcWorkingBeamWordCommand;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 專責將 WorkingBeamTask 組裝為 PlcWorkingBeamWordCommand 的組裝器
 */
@Component
@RequiredArgsConstructor
public class WorkingBeamWordCommandAssembler {

    public PlcWorkingBeamWordCommand assemble(WorkingBeamTask task) {
        return PlcWorkingBeamWordCommand.builder()
                .transferNo(task.getId().intValue())
                .transferType(1) // 固定為 1: Move
                .direction(parseDirection(task.getDirection()))
                .build();
    }

    private int parseDirection(String direction) {
        return switch (direction) {
            case "IN" -> 1;
            case "OUT" -> 2;
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        };
    }
}
