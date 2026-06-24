package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.process.*;
import com.czkuo.rdf88701.infra.cache.DeviceProcessStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/**
 * 流程狀態輪詢 Monitor。
 *
 * <p>定期查詢 WIP、拆併、ZIPA、ZIPB 等流程狀態，並更新 DeviceProcessStateCache
 * 供其他服務或前端查詢使用。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStateMonitor {

    private final WipPlcProcessStateProvider wipProvider;   // WIP + 拆併
    private final ZipProcessStateProvider zipProvider;      // ZIPA + ZIPB
    private final DeviceProcessStateCache cache;

    // WIP (每 2 秒)
    @Scheduled(fixedDelayString = "${monitor.process.wip-interval-ms:800}")
    public void pollWip() {
        var st = wipProvider.queryWip();
        cache.update(st);
        //log.debug("[ProcessMonitor] {}", st);
    }

    // 拆併 (每 2 秒)
    @Scheduled(fixedDelayString = "${monitor.process.split-interval-ms:600}")
    public void pollSplit() {
        var st = wipProvider.querySplit();
        cache.update(st);
        //log.debug("[ProcessMonitor] {}", st);
    }

    // ZIPA (每 5 秒)
    @Scheduled(fixedDelayString = "${monitor.process.zipa-interval-ms:5000}")
    public void pollZipa() {
        var st = zipProvider.queryZipa();
        cache.update(st);
        //log.debug("[ProcessMonitor] {}", st);
    }

    // ZIPB (每 5 秒)
    @Scheduled(fixedDelayString = "${monitor.process.zipb-interval-ms:5000}")
    public void pollZipb() {
        var st = zipProvider.queryZipb();
        cache.update(st);
        //log.debug("[ProcessMonitor] {}", st);
    }
}
