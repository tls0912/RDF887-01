package com.czkuo.rdf88701.infra.init;

import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcClientManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系統啟動時，自動初始化所有已啟用的 PLC 裝置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcDeviceInitializer {

    private final PlcProperties plcProperties;
    private final PlcClientManager clientManager;

    @PostConstruct
    public void initDevices() {
        log.info("[PLC] 系統啟動中，開始初始化所有已啟用的 PLC 裝置...");
        clientManager.initAllDevices(plcProperties.getDevices());
        log.info("[PLC] 初始化程序完成");
    }
}
