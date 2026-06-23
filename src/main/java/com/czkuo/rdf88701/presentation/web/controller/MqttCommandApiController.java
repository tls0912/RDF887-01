package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.mqtt.MqttCommandService;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import com.czkuo.rdf88701.common.dto.mqtt.command.A010CommandPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S015CommandPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S020CommandPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.S073CommandPayload;
import com.czkuo.rdf88701.presentation.web.dto.S020EventRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * MQTT 指令發送 API
 * - 提供外部系統或 UI 發送 MQTT 指令的入口
 * - 支援 SEEC / ASE 為接收端的場景
 */
@Slf4j
@RestController
@RequestMapping("/api/mqtt/command")
@RequiredArgsConstructor
@Validated
public class MqttCommandApiController {

    private final MqttCommandService mqttCommandService;

    /**
     * 發送 S001 握手指令（由本系統主動發送至目標）
     */
    @PostMapping("/s001")
    public ResponseResult<MqttSendResult> sendS001(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String programName,
            @RequestParam @NotBlank String version,
            @RequestParam(defaultValue = "manual") String senderHint
    ) {
        log.info("[API] 收到發送 S001 請求：receiver={}, program={}, version={}, senderHint={}",
                receiver, programName, version, senderHint);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS001(targetSystem, programName, version, senderHint);

        log.info("[API] S001 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S002 系統心跳指令（雙向皆可主動發送）
     */
    @PostMapping("/s002")
    public ResponseResult<MqttSendResult> sendS002(
            @RequestParam @NotBlank String receiver
    ) {
        log.info("[API] 收到發送 S002 請求：receiver={}", receiver);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS002(targetSystem);

        log.info("[API] S002 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S007 警報事件通知
     */
    @PostMapping("/s007")
    public ResponseResult<MqttSendResult> sendS007(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String alid,
            @RequestParam @NotBlank String alidDescEn,
            @RequestParam @NotBlank String alidDescCh,
            @RequestParam @NotBlank String alarmCode
    ) {
        log.info("[API] 收到發送 S007 警報通知請求：receiver={}, deviceName={}, alid={}, alarmCode={}",
                receiver, deviceName, alid, alarmCode);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS007(targetSystem,
                deviceName, alid, alidDescEn, alidDescCh, alarmCode);

        log.info("[API] S007 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S008 警告事件通知
     */
    @PostMapping("/s008")
    public ResponseResult<MqttSendResult> sendS008(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String alid,
            @RequestParam @NotBlank String alidDescEn,
            @RequestParam @NotBlank String alidDescCh,
            @RequestParam @NotBlank String alarmCode
    ) {
        log.info("[API] 收到發送 S008 警告通知請求：receiver={}, deviceName={}, alid={}", receiver, deviceName, alid);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS008(targetSystem, deviceName, alid, alidDescEn, alidDescCh, alarmCode);

        log.info("[API] S008 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S010 刷卡驗證請求
     */
    @PostMapping("/s010")
    public ResponseResult<MqttSendResult> sendS010(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String cardNumber,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String safeDoorName
    ) {
        log.info("[API] 收到發送 S010 刷卡驗證請求：receiver={}, cardNumber={}, deviceName={}, safeDoorName={}", receiver, cardNumber, deviceName, safeDoorName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS010(targetSystem, cardNumber, deviceName, safeDoorName);

        log.info("[API] S010 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S011 開門資格驗證指令
     */
    @PostMapping("/s011")
    public ResponseResult<MqttSendResult> sendS011(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String safeDoorName
    ) {
        log.info("[API] 收到發送 S011 開門資格驗證請求：receiver={}, deviceName={}, safeDoorName={}", receiver, deviceName, safeDoorName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS011(targetSystem, deviceName, safeDoorName);

        log.info("[API] S011 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S012 關門資格驗證指令 API
     */
    @PostMapping("/s012")
    public ResponseResult<MqttSendResult> sendS012(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String safeDoorName
    ) {
        log.info("[API] 收到發送 S012 關門資格驗證請求：receiver={}, deviceName={}, safeDoorName={}", receiver, deviceName, safeDoorName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS012(targetSystem, deviceName, safeDoorName);

        log.info("[API] S012 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S013 RESET/START 驗證指令 API
     *
     * @param receiver 接收端系統（如 ase）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s013")
    public ResponseResult<MqttSendResult> sendS013(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName
    ) {
        log.info("[API] 收到發送 S013 RESET/START 驗證請求：receiver={}, deviceName={}", receiver, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS013(targetSystem, deviceName);

        log.info("[API] S013 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S014 零件預警清單指令 API
     *
     * @param receiver 接收端系統（如 seec）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s014")
    public ResponseResult<MqttSendResult> sendS014(
            @RequestParam @NotBlank String receiver
    ) {
        log.info("[API] 收到發送 S014 零件預警清單請求：receiver={}", receiver);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS014(targetSystem);

        log.info("[API] S014 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S015 零件預警設定指令 API
     *
     * @param receiver    接收端系統（如 seec）
     * @param toolSettings 預警設定清單（包含工具名稱、上限與單位）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s015")
    public ResponseResult<MqttSendResult> sendS015(
            @RequestParam @NotBlank String receiver,
            @RequestBody @NotEmpty List<S015CommandPayload.ToolSetting> toolSettings
    ) {
        log.info("[API] 收到發送 S015 零件預警設定請求：receiver={}, toolSettingsCount={}", receiver, toolSettings.size());

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS015(targetSystem, toolSettings);

        log.info("[API] S015 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S016 系統校時指令 API
     *
     * @param receiver 接收端系統（如 seec）
     * @param datetime 要同步的時間（格式為 yyyyMMddHHmmss）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s016")
    public ResponseResult<MqttSendResult> sendS016(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String datetime
    ) {
        log.info("[API] 收到發送 S016 系統校時請求：receiver={}, datetime={}", receiver, datetime);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS016(targetSystem, datetime);

        log.info("[API] S016 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * （向下相容）S020 事件上報 - 舊版（query 參數）
     * 仍可用，但內部改走新的 Generic 發送；不再限制只能 3 個文字欄位。
     */
    @PostMapping("/s020")
    public ResponseResult<MqttSendResult> sendS020(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String ceid,
            @RequestParam @NotBlank String ceidDescEn,
            @RequestParam @NotBlank String ceidDescCh
    ) {
        log.info("[API] S020(v1) req: receiver={}, ceid={}", receiver, ceid);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);

        // 轉成新的 Message，再用 Generic 送出
        S020CommandPayload.Message msg = S020CommandPayload.Message.builder()
                .ceid(ceid)
                .ceidDescEn(ceidDescEn)
                .ceidDescCh(ceidDescCh)
                .build();

        MqttSendResult result = mqttCommandService.sendS020Generic(targetSystem, msg);

        log.info("[API] S020(v1) result: TID={}, ok={}, msg={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result)
                : ResponseResult.fail(result.getMessage());
    }

    /**
     * S020 事件上報 - 新版（JSON）
     * 可攜帶 STATUS / LOT_ID / CARRIERID / TYPE / WIPNAME / NUM / 1D_BARCODE 等
     *
     * 範例：
     * POST /api/mqtt/s020/v2
     * Content-Type: application/json
     * {
     *   "receiver": "ase",
     *   "ceid": "2003",
     *   "ceidDescEn": "",
     *   "ceidDescCh": "拆/併打帶完成，等待標籤資訊",
     *   "LOT_ID": "11YT11V001",
     *   "CARRIERID": "TY0001VM"
     * }
     */
    @PostMapping(value = "/s020/v2")
    public ResponseResult<MqttSendResult> sendS020_v2(@RequestBody @Valid S020EventRequest req) {
        String targetSystem = req.getReceiver().toLowerCase(Locale.ROOT);
        log.info("[API] S020(v2) req: receiver={}, ceid={}, descCh={}",
                targetSystem, req.getCeid(), req.getCeidDescCh());

        // 直接把可選欄位對應到新的 Message
        S020CommandPayload.Message msg = S020CommandPayload.Message.builder()
                .ceid(req.getCeid())
                .ceidDescEn(req.getCeidDescEn())
                .ceidDescCh(req.getCeidDescCh())
                .status(req.getStatus())
                .lotId(req.getLotId())
                .carrierId(req.getCarrierId())
                .type(req.getType())
                .wipname(req.getWipname())
                .num(req.getNum())
                .oneDBarcode(req.getOneDBarcode())
                .build();

        MqttSendResult result = mqttCommandService.sendS020Generic(targetSystem, msg);

        log.info("[API] S020(v2) result: TID={}, ok={}, msg={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result)
                : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 S067 電池資訊回拋指令 API
     *
     * @param receiver 接收端系統（如 seec）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s067")
    public ResponseResult<MqttSendResult> sendS067(
            @RequestParam @NotBlank String receiver
    ) {
        log.info("[API] 收到發送 S067 電池資訊回拋請求：receiver={}", receiver);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS067(targetSystem);

        log.info("[API] S067 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S068 打帶前狀態確認指令 API
     *
     * @param receiver 接收端系統（如 ase）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s068")
    public ResponseResult<MqttSendResult> sendS068(
            @RequestParam @NotBlank String receiver
    ) {
        log.info("[API] 收到發送 S068 打帶前狀態確認請求：receiver={}", receiver);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS068(targetSystem);

        log.info("[API] S068 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 S073 拆併前 Tray 圖像確認指令 API
     *
     * @param receiver  接收端系統（如 ase）
     * @param lotId     生產批號
     * @param trayType  Tray 類型代號
     * @param trayDesc  Tray 說明（型號/尺寸等）
     * @param message   包含所有 Tray 圖像欄位的 Message DTO（含 byte[]）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/s073")
    public ResponseResult<MqttSendResult> sendS073(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String lotId,
            @RequestParam @NotBlank String trayType,
            @RequestParam @NotBlank String trayDesc,
            @RequestBody S073CommandPayload.Message message
    ) {
        log.info("[API] 收到發送 S073 拆併前 Tray 圖像確認請求：receiver={}, lotId={}, trayType={}, trayDesc={}",
                receiver, lotId, trayType, trayDesc);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendS073(targetSystem, lotId, trayType, trayDesc, message);

        log.info("[API] S073 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        if (result.isSuccess()) {
            return ResponseResult.ok(result);
        } else {
            return ResponseResult.fail(result.getMessage());
        }
    }

    /**
     * 發送 L005 條碼檢查指令 API
     *
     * @param receiver 接收端系統（如 ase）
     * @param barcode  條碼內容（1D Barcode）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/l005")
    public ResponseResult<MqttSendResult> sendL005(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String barcode
    ) {
        log.info("[API] 收到發送 L005 條碼檢查請求：receiver={}, barcode={}", receiver, barcode);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendL005(targetSystem, barcode);

        log.info("[API] L005 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 R007 任務指令（WIP → 機台）API
     *
     * @param receiver        接收端系統（如 seec）
     * @param lotId           批號
     * @param carrierId       載具 ID
     * @param wipName         起始儲格名稱
     * @param destLoc         目的地設備位置
     * @param eqpPort         接收端 Port（如 X1）
     * @param deviceName      指定執行任務的 AGV 名稱
     * @param stkPort         起始 port（如 STK01）
     * @param movePriority    （選填）移載優先順序
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/r007")
    public ResponseResult<MqttSendResult> sendR007(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String lotId,
            @RequestParam @NotBlank String carrierId,
            @RequestParam @NotBlank String wipName,
            @RequestParam @NotBlank String destLoc,
            @RequestParam @NotBlank String eqpPort,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String stkPort,
            @RequestParam(required = false) Integer movePriority
    ) {
        log.info("[API] 收到發送 R007 任務指令請求：receiver={}, lotId={}, deviceName={}",
                receiver, lotId, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendR007(
                targetSystem, lotId, carrierId, wipName, destLoc,
                eqpPort, deviceName, stkPort, movePriority);

        log.info("[API] R007 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 R008 任務指令（通知從機台A搬貨到 WIP(STK)）API
     *
     * @param receiver        接收端系統（如 seec）
     * @param lotId           批號
     * @param carrierId       載具 ID
     * @param wipName         目的地儲格名稱（WIP 名稱）
     * @param destLoc         來源機台代碼（機台 A）
     * @param eqpPort         來源機台 Port（如 X1）
     * @param deviceName      指定執行任務的 AGV 名稱
     * @param stkPort         目的地 STK Port（可選）
     * @param movePriority    （選填）移載優先順序
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/r008")
    public ResponseResult<MqttSendResult> sendR008(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String lotId,
            @RequestParam @NotBlank String carrierId,
            @RequestParam @NotBlank String wipName,
            @RequestParam @NotBlank String destLoc,
            @RequestParam @NotBlank String eqpPort,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String stkPort,
            @RequestParam(required = false) Integer movePriority
    ) {
        log.info("[API] 收到發送 R008 任務指令請求：receiver={}, lotId={}, deviceName={}",
                receiver, lotId, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendR008(
                targetSystem, lotId, carrierId, wipName, destLoc,
                eqpPort, deviceName, stkPort, movePriority);

        log.info("[API] R008 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 R018 指令：刪除任務 API
     *
     * @param receiver 接收端系統（如 seec）
     * @param cmdTid   欲刪除的任務識別碼（格式為 CMD_ID_TID，例如 R007_20240729132015999）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/r018")
    public ResponseResult<MqttSendResult> sendR018(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String cmdTid
    ) {
        log.info("[API] 收到發送 R018 刪除任務請求：receiver={}, cmdTid={}", receiver, cmdTid);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendR018(targetSystem, cmdTid);

        log.info("[API] R018 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 R030 指令：從 E-Rack 搬貨至機台 API
     *
     * @param receiver    接收端系統（如 seec）
     * @param lotId       批號
     * @param carrierId   搬運對象（如料盤 ID）
     * @param wipName     儲格名稱
     * @param destLoc     搬運目的地（機台代碼）
     * @param eqpPort     機台 port
     * @param deviceName  指定執行的 AGV 裝置名稱
     * @param stkPort     STK port
     * @param agvSpeed    AGV 移動速度
     * @param armSpeed    機械手臂速度
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/r030")
    public ResponseResult<MqttSendResult> sendR030(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String lotId,
            @RequestParam @NotBlank String carrierId,
            @RequestParam @NotBlank String wipName,
            @RequestParam @NotBlank String destLoc,
            @RequestParam @NotBlank String eqpPort,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String stkPort,
            @RequestParam @NotBlank String agvSpeed,
            @RequestParam @NotBlank String armSpeed
    ) {
        log.info("[API] 收到發送 R030 任務指令請求：receiver={}, lotId={}, deviceName={}",
                receiver, lotId, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendR030(
                targetSystem, lotId, carrierId, wipName, destLoc,
                eqpPort, deviceName, stkPort, agvSpeed, armSpeed);

        log.info("[API] R030 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A008 AGV 車事件指令 API
     *
     * @param receiver   接收端系統（如 saa）
     * @param deviceName AGV 名稱（例如 "AGV01"）
     * @param status     當前運作狀態（如 RUN、IDLE、ERROR）
     * @param commandId  指令識別碼（綁定原始任務指令）
     * @param carrierId  載具名稱（如有）
     * @param battery    電量百分比（例如 "85"）
     * @param destLoc    目前位置（如 EQP、STK、POWER_STATION）
     * @param jobStatus  任務狀態進度（如 INPUT_START、OUTPUT_END）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a008")
    public ResponseResult<MqttSendResult> sendA008(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String status,
            @RequestParam @NotBlank String commandId,
            @RequestParam String carrierId,
            @RequestParam @NotBlank String battery,
            @RequestParam @NotBlank String destLoc,
            @RequestParam @NotBlank String jobStatus
    ) {
        log.info("[API] 收到發送 A008 AGV 車事件指令請求：receiver={}, deviceName={}", receiver, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA008(
                targetSystem, deviceName, status, commandId, carrierId, battery, destLoc, jobStatus);

        log.info("[API] A008 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A009 指令 API
     *
     * @param receiver 接收端系統（如 seec）
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a009")
    public ResponseResult<MqttSendResult> sendA009(
            @RequestParam @NotBlank String receiver
    ) {
        log.info("[API] 收到發送 A009 AGV 車輛狀態查詢請求：receiver={}", receiver);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA009(targetSystem);

        log.info("[API] A009 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A010 指令 API
     *
     * @param receiver      接收端系統（如 saa）
     * @param agvStatusList 多台 AGV 狀態清單
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a010")
    public ResponseResult<MqttSendResult> sendA010(
            @RequestParam @NotBlank String receiver,
            @RequestBody @NotEmpty List<A010CommandPayload.AgvStatus> agvStatusList
    ) {
        log.info("[API] 收到發送 A010 AGV 狀態回拋請求：receiver={}, agvCount={}", receiver, agvStatusList.size());

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA010(targetSystem, agvStatusList);

        log.info("[API] A010 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A013 AGV 離開換電站狀態回報指令 API
     *
     * @param receiver     接收端系統（如 saa）
     * @param deviceName   AGV 車輛 ID
     * @param batteryId    換電後所使用的電池 ID
     * @param batteryValue 電量百分比
     * @param odo          總里程
     * @param trip         TRIP 里程
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a013")
    public ResponseResult<MqttSendResult> sendA013(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String batteryId,
            @RequestParam @NotBlank String batteryValue,
            @RequestParam @NotBlank String odo,
            @RequestParam @NotBlank String trip
    ) {
        log.info("[API] 收到發送 A013 AGV 離開換電站狀態回報請求：receiver={}, deviceName={}", receiver, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA013(targetSystem, deviceName, batteryId, batteryValue, odo, trip);

        log.info("[API] A013 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A014 AGV 回到換電站指令 API
     *
     * @param receiver     接收端系統（如 saa）
     * @param deviceName   AGV 車輛 ID
     * @param batteryId    換電後所使用的電池 ID
     * @param batteryValue 電量百分比
     * @param odo          總里程
     * @param trip         TRIP 里程
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a014")
    public ResponseResult<MqttSendResult> sendA014(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String batteryId,
            @RequestParam @NotBlank String batteryValue,
            @RequestParam @NotBlank String odo,
            @RequestParam @NotBlank String trip
    ) {
        log.info("[API] 收到發送 A014 AGV 回到換電站請求：receiver={}, deviceName={}", receiver, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA014(targetSystem, deviceName, batteryId, batteryValue, odo, trip);

        log.info("[API] A014 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /**
     * 發送 A015 AGV 抵達 EQP 指令 API
     *
     * @param receiver   接收端系統（如 saa）
     * @param tid        原始任務 TID（用於追蹤）
     * @param deviceName AGV 裝置名稱
     * @param destLoc    到達位置
     * @return 發送結果，包含 TID、成功狀態與訊息
     */
    @PostMapping("/a015")
    public ResponseResult<MqttSendResult> sendA015(
            @RequestParam @NotBlank String receiver,
            @RequestParam @NotBlank String tid,
            @RequestParam @NotBlank String deviceName,
            @RequestParam @NotBlank String destLoc
    ) {
        log.info("[API] 收到發送 A015 AGV 抵達 EQP 指令請求：receiver={}, tid={}, deviceName={}", receiver, tid, deviceName);

        String targetSystem = receiver.toLowerCase(Locale.ROOT);
        MqttSendResult result = mqttCommandService.sendA015(targetSystem, tid, deviceName, destLoc);

        log.info("[API] A015 發送結果：TID={}, success={}, message={}",
                result.getTid(), result.isSuccess(), result.getMessage());

        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }
}
