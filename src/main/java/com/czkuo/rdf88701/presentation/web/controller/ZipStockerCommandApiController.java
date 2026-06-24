package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.CancelDispatchOrder.CancelDispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.DispatchOrder.DispatchOrderSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQueryPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.checktimer.CheckTimerSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCS → ZIP 指令 API
 * - 對內/對外提供發送 ZIP Primary 指令的入口，回傳 ZIP 的 Secondary 結果
 * - 比照 MQTT Controller，用 ResponseResult 包裝
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@RestController
@RequestMapping("/api/zip/command")
@RequiredArgsConstructor
@Validated
public class ZipStockerCommandApiController {

    private final ZipStockerCommandService service; // 直接注入具體類別（無介面）

    /** CheckTimer：MCS 校時到 ZIP */
    @PostMapping("/check-timer")
    public ResponseResult<Root<CheckTimerSecondaryBody>> checkTimer(
            @RequestParam int year, @RequestParam int mon, @RequestParam int day,
            @RequestParam int hour, @RequestParam int minute, @RequestParam int second,
            @RequestParam(name = "target", defaultValue = "ZIPA") ZipTarget target
    ) {
        log.info("[ZIP CMD] CheckTimer: {}-{}-{} {}:{}:{}, target={}",
                year, mon, day, hour, minute, second, target);
        Root<CheckTimerSecondaryBody> resp =
                service.sendCheckTimer(target, year, mon, day, hour, minute, second);
        return ResponseResult.ok(resp);
    }

    /** DispatchOrder：出貨命令（Magazines + 選填 STK_PORT） */
    @PostMapping("/dispatch")
    public ResponseResult<Root<DispatchOrderSecondaryBody>> dispatch(
            @RequestBody @NotEmpty List<String> magazines,
            @RequestParam(required = false) String stkPort,
            @RequestParam(name = "target", defaultValue = "ZIPA") ZipTarget target
    ) {
        log.info("[ZIP CMD] DispatchOrder: magazines={}, stkPort={}, target={}", magazines, stkPort, target);
        Root<DispatchOrderSecondaryBody> resp = service.sendDispatchOrder(target, magazines, stkPort);
        return ResponseResult.ok(resp);
    }

    /** CancelDispatchOrder：取消出貨命令 */
    @PostMapping("/cancel")
    public ResponseResult<Root<CancelDispatchOrderSecondaryBody>> cancel(
            @RequestBody @NotEmpty List<String> magazines,
            @RequestParam(name = "target", defaultValue = "ZIPA") ZipTarget target
    ) {
        log.info("[ZIP CMD] CancelDispatchOrder: magazines={}, target={}", magazines, target);
        Root<CancelDispatchOrderSecondaryBody> resp = service.sendCancelDispatchOrder(target, magazines);
        return ResponseResult.ok(resp);
    }

    /** PortLockUnlock：Port 鎖定/解鎖 */
    @PostMapping("/port-lock")
    public ResponseResult<Root<PortLockUnlockSecondaryBody>> portLockUnlock(
            @RequestParam @NotBlank String portName,
            @RequestParam int cmd, // 1=Lock, 2=Unlock
            @RequestParam(name = "target", defaultValue = "ZIPA") ZipTarget target
    ) {
        log.info("[ZIP CMD] PortLockUnlock: port={}, cmd={}, target={}", portName, cmd, target);
        Root<PortLockUnlockSecondaryBody> resp = service.sendPortLockUnlock(target, portName, cmd);
        return ResponseResult.ok(resp);
    }

    /** StatusQuery：詢問狀態（可送多筆 QueryInfos） */
    @PostMapping("/status-query")
    public ResponseResult<Root<StatusQuerySecondaryBody>> statusQuery(
            @RequestBody @NotEmpty List<StatusQueryPrimaryBody.QueryInfo> queries,
            @RequestParam(name = "target", defaultValue = "ZIPA") ZipTarget target
    ) {
        log.info("[ZIP CMD] StatusQuery: count={}, target={}", queries.size(), target);
        Root<StatusQuerySecondaryBody> resp = service.sendStatusQuery(target, queries);
        return ResponseResult.ok(resp);
    }
}
