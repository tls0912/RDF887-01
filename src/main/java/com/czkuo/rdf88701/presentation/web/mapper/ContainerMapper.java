package com.czkuo.rdf88701.presentation.web.mapper;

import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.presentation.web.dto.ContainerMainDto;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public final class ContainerMapper {
    private ContainerMapper() {}

    /** 舊簽名（相容保留）：厚度預設為 null */
    public static ContainerMainDto toDto(ContainerMain m, ContainerData data) {
        return toDto(m, data, (Double) null);
    }

    /** 新簽名：直接給已解析好的厚度（mm） */
    public static ContainerMainDto toDto(ContainerMain m, ContainerData data, Double trayThicknessMm) {
        Integer verified = data != null ? data.getVerifiedQuantity() : null;
        Integer estimated = data != null ? data.getEstimatedQuantity() : null;
        Integer coverLayers   = data != null ? data.getCoverLayers()   : null;
        Integer productLayers = data != null ? data.getProductLayers() : null;
        String  ocr1     = data != null ? data.getOcrText1() : null;
        String  ocr2     = data != null ? data.getOcrText2() : null;
        String  kind     = data != null ? data.getContentKind() : null;

        return new ContainerMainDto(
                m.getId(),
                m.getAliasCode(),
                m.getContainerType(),
                m.getContainerCode(),
                m.getLotNo(),
                m.getPartNo(),
                m.getCreatedTime(),
                verified,
                estimated,
                coverLayers,
                productLayers,
                ocr1,
                ocr2,
                kind,
                trayThicknessMm
        );
    }

    /** 方便用 attr 原始字串呼叫（例如 "5.62mm" / "5,62"） */
    public static ContainerMainDto toDtoWithThickness(ContainerMain m, ContainerData data, String trayThicknessMmRaw) {
        return toDto(m, data, parseThicknessMm(trayThicknessMmRaw));
    }

    /** 寬鬆解析厚度字串成 Double（mm）；失敗回 null */
    private static Double parseThicknessMm(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
