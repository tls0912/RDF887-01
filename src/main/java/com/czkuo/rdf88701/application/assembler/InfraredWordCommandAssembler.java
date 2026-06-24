package com.czkuo.rdf88701.application.assembler;

import com.czkuo.rdf88701.domain.repository.ContainerAttrRepository;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 專責將 InfraredTask 組裝為 PlcInfraredWordCommand 的組裝器
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredWordCommandAssembler {

    private static final String ATTR_TRAY_THICKNESS_MM = "tray_thickness_mm";

    private final ContainerAttrRepository containerAttrRepository;

    public PlcInfraredWordCommand assemble(InfraredTask task) {
        final Long containerId = task.getContainerMainId();

        if (containerId == null) {
            throw new IllegalStateException("[Infrared] container_main_id 為空，無法取得托盤厚度屬性");
        }

        final int trayThicknessX100 = resolveTrayThicknessX100(containerId);

        return PlcInfraredWordCommand.builder()
                .measureNo(task.getId().intValue())
                .taskType(parseTaskType(task.getTaskType()))
                .trayThickness(trayThicknessX100) // mm * 100，供 PLC 使用
                .build();
    }

    private int parseTaskType(String taskType) {
        // 依紅外線協定定義，假設 MEASURE 固定為 1
        return switch (taskType) {
            case "MEASURE" -> 1;
            // 如日後有其他類型可擴充
            default -> throw new IllegalArgumentException("Invalid taskType: " + taskType);
        };
    }

    /**
     * 讀取 tray_thickness_mm（字串 mm），轉成 mm*100 的整數（保留 2 位小數，四捨五入）
     */
    private int resolveTrayThicknessX100(Long containerId) {
        ContainerAttr attr = containerAttrRepository
                .findOne(containerId, ATTR_TRAY_THICKNESS_MM)
                .orElseThrow(() -> new IllegalStateException(
                        "[Infrared] 找不到屬性 " + ATTR_TRAY_THICKNESS_MM + "，containerId=" + containerId));

        String raw = attr.getAttrValue();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("[Infrared] 屬性 tray_thickness_mm 為空，containerId=" + containerId);
        }

        try {
            BigDecimal mm = new BigDecimal(raw.trim());
            // 轉成 ×100 的整數（0.01mm解析度），例：1.23 mm -> 123
            BigDecimal x100 = mm.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);

            // 基本防呆：0~65535（單一 WORD 可容納），若你的協定允許更大可調整
            int val = x100.intValueExact();
            if (val < 0 || val > 0xFFFF) {
                throw new IllegalStateException("[Infrared] tray_thickness_mm 超出允許範圍（0..655.35 mm），raw=" + raw);
            }
            return val;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalStateException("[Infrared] 解析 tray_thickness_mm 失敗，raw=" + raw, ex);
        }
    }
}
