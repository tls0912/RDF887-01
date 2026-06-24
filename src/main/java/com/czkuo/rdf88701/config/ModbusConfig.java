package com.czkuo.rdf88701.config;

import com.czkuo.rdf88701.config.modbus.CameraModbusProperties;
import com.github.xingshuangs.iot.protocol.modbus.service.ModbusTcp;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Configuration
@RequiredArgsConstructor
public class ModbusConfig {

    private final CameraModbusProperties props;
    private final org.springframework.core.env.Environment env;

    @Bean(destroyMethod = "close")
    public ModbusTcp modbusTcp() {
        // 教程示例預設長連線（持久化為 true）
        ModbusTcp plc = new ModbusTcp(props.getHost(), props.getPort());
        // 固定 unitId（1 對 1 模式），亦可改用帶 unitId 的方法多從站
        plc.setUnitId(props.getUnitId());
        plc.setPersistence(true);

        boolean debug = Boolean.parseBoolean(
                env.getProperty("camera.modbusDebugLog", "false"));
        if (debug) {
            plc.setComCallback((tag, bytes) ->
                    System.out.printf("%s[%d] %s%n", tag, bytes.length,
                            com.github.xingshuangs.iot.utils.HexUtil.toHexString(bytes)));
        }
        return plc;
    }
}
