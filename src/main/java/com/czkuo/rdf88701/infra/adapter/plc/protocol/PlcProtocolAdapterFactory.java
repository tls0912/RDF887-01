package com.czkuo.rdf88701.infra.adapter.plc.protocol;

import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.mc.McProtocolAdapter;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.options.McOptions;
import com.czkuo.rdf88701.infra.adapter.plc.protocol.options.PlcOptionMapper;
import com.github.xingshuangs.iot.protocol.melsec.enums.EMcSeries;
import com.github.xingshuangs.iot.protocol.melsec.service.McPLC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 協議轉接器工廠類別，可根據協定建立對應的協議實作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcProtocolAdapterFactory {

    private final PlcOptionMapper plcOptionMapper;

    /** 已初始化的 adapter 快取 */
    private final Map<String, PlcProtocolAdapter> adapterCache = new ConcurrentHashMap<>();

    /**
     * 取得或建立協議轉接器
     *
     * @param device 裝置設定資訊
     * @return 對應的協議轉接器
     */
    public PlcProtocolAdapter getOrCreateAdapter(PlcProperties.Device device) {
        return adapterCache.computeIfAbsent(device.getName(), name -> {
            try {
                return createAdapter(device);
            } catch (Exception e) {
                log.error("[PLC] 建立協議轉接器失敗: {}", name, e);
                throw new RuntimeException("PLC adapter creation failed for device: " + name, e);
            }
        });
    }

    /**
     * 建立新的協議轉接器
     */
    private PlcProtocolAdapter createAdapter(PlcProperties.Device device) {
        String protocol = device.getProtocol().toLowerCase();

        return switch (protocol) {
            case "mc" -> createMcAdapter(device);
            // 預留其他協議支援
            default -> throw new UnsupportedOperationException(
                    "Unsupported PLC protocol: " + protocol + " (device: " + device.getName() + ")"
            );
        };
    }

    /**
     * 建立 MC 協議轉接器
     */
    private PlcProtocolAdapter createMcAdapter(PlcProperties.Device device) {
        McOptions options = plcOptionMapper.convert(device.getOptions(), McOptions.class);
        EMcSeries series = parseMcSeries(options.getSeries());

        McPLC client = new McPLC(series, device.getIp(), device.getPort());

        // 設定 timeout（若有提供）
        if (options.getConnectTimeout() != null) {
            client.setConnectTimeout(options.getConnectTimeout());
        }
        if (options.getReceiveTimeout() != null) {
            client.setReceiveTimeout(options.getReceiveTimeout());
        }

        log.info("[PLC] 建立 MC Adapter：device='{}', ip='{}', port={}, series={}, connectTimeout={}, receiveTimeout={}",
                device.getName(),
                device.getIp(),
                device.getPort(),
                series,
                client.getConnectTimeout(),
                client.getReceiveTimeout());

        return new McProtocolAdapter(client);
    }

    /**
     * 將字串轉換為 EMcSeries，若無效則回傳預設 IQ_R
     */
    private EMcSeries parseMcSeries(String value) {
        if (value == null || value.isBlank()) {
            log.warn("[PLC] MC 系列未指定，使用預設: IQ_R");
            return EMcSeries.IQ_R;
        }

        try {
            return EMcSeries.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("[PLC] 不合法的 MC 系列 '{}', 使用預設: IQ_R", value);
            return EMcSeries.IQ_R;
        }
    }

    /**
     * 重設快取（測試或手動重載時使用）
     */
    public void clearCache() {
        adapterCache.clear();
    }
}
