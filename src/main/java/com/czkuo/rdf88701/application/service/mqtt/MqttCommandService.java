package com.czkuo.rdf88701.application.service.mqtt;

import com.czkuo.rdf88701.application.mqtt.util.BaseMqttHandlerUtils;
import com.czkuo.rdf88701.common.dto.MqttSendResult;
import com.czkuo.rdf88701.common.dto.mqtt.command.*;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * MqttCommandService
 * - 統一封裝 MQTT 指令的發送流程
 * - 包含 TID 產生、Payload 組裝、序列化、送出/入箱、結果回傳
 * <p>
 * 設計重點
 * - 預設採用 Outbox 可靠推送：入箱 + 立即嘗試一次；重送與 ACK 收斂由 Outbox/Worker 處理
 * - 若要改回「直接發送」：設定 mqtt.command.use-outbox=false
 * - ID_DESC 維持固定字串，不吃設定檔
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttCommandService {

    private final MqttDirectMessageSender messageSender;
    private final ObjectMapper objectMapper;
    private final MqttEventOutboxService outbox;

    /**
     * 是否走 Outbox（預設 true；false 則改為直接送 MQTT）
     */
    @Value("${mqtt.command.use-outbox:true}")
    private boolean useOutbox;

    /* ===================================================================== */
    /* 共用：TID 選擇器與通用發布                                              */
    /* ===================================================================== */

    /**
     * 若呼叫端有提供 TID 就用，否則自動產生
     */
    private String pickTid(String providedTid) {
        return (providedTid != null && !providedTid.isBlank())
                ? providedTid
                : BaseMqttHandlerUtils.generateTid();
    }

    /**
     * 以「自動 TID」發布任意指令（物件 payload 版）
     * - 不沿用外部 TID，僅供臨時快速直送
     */
    public MqttSendResult publishAutoTid(String targetSystem,
                                         String cmdId,
                                         Object payloadObject,
                                         boolean requireAck) {
        String tid = BaseMqttHandlerUtils.generateTid();
        try {
            String json = objectMapper.writeValueAsString(payloadObject);
            return dispatch(targetSystem, cmdId, json, tid, requireAck);
        } catch (Exception e) {
            log.error("[MQTT] publishAutoTid 發送失敗：cmdId={}, err={}", cmdId, e.getMessage(), e);
            return MqttSendResult.fail("publishAutoTid 發送失敗：" + e.getMessage(), null);
        }
    }

    /* ===================================================================== */
    /* 便捷工具：開機上報 S001 可直接呼叫，不必每次都塞 program/version           */
    /* ===================================================================== */

    /**
     * 便捷方法：用推導出的 program/version，並以 "auto-startup" 為 hint 發送 S001
     * - 適合在 ApplicationReady 時做「軟體開啟上報 S001」
     */
    public MqttSendResult sendS001Auto(String targetSystem) {
        return sendS001(targetSystem, resolveProgramName(), resolveVersion(), "auto-startup");
    }

    /**
     * 便捷方法：用推導出的 program/version，自訂 hint
     */
    public MqttSendResult sendS001Auto(String targetSystem, String hint) {
        return sendS001(targetSystem, resolveProgramName(), resolveVersion(), hint);
    }

    /* ===================================================================== */
    /* 各指令發送（依你既有 DTO，維持 ID_DESC 固定、欄位命名不變）               */
    /* ===================================================================== */

    /**
     * 發送 S001 握手指令（雙向皆可主動發送）
     * - requireAck：true
     */
    public MqttSendResult sendS001(String targetSystem, String programName, String version, String hint) {
        return sendS001WithTid(targetSystem, null, programName, version, hint);
    }

    public MqttSendResult sendS001WithTid(String targetSystem, String tid, String programName, String version, String hint) {
        String useTid = pickTid(tid);
        try {
            S001CommandPayload payload = new S001CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S001");
            payload.setIdDesc("PC_LINK");
            payload.setTid(useTid);

            S001CommandPayload.Message message = new S001CommandPayload.Message();
            message.setProgramName(programName);
            message.setVersion(version);
            message.setHint(hint);
            payload.setMessage(message);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S001", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S001 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S001 指令發送異常：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S002 系統心跳（雙向皆可主動發送）
     * - requireAck：true
     */
    public MqttSendResult sendS002(String targetSystem) {
        return sendS002WithTid(targetSystem, null);
    }

    public MqttSendResult sendS002WithTid(String targetSystem, String tid) {
        String useTid = pickTid(tid);
        try {
            S002CommandPayload payload = new S002CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S002");
            payload.setIdDesc("CHECK_READY");
            payload.setTid(useTid);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S002", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S002 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S002 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S007 警報事件通知
     * - requireAck：false
     */
    public MqttSendResult sendS007(String targetSystem,
                                   String deviceName,
                                   String alid,
                                   String alidDescEn,
                                   String alidDescCh,
                                   String alarmCode) {
        return sendS007WithTid(targetSystem, null, deviceName, alid, alidDescEn, alidDescCh, alarmCode);
    }

    public MqttSendResult sendS007WithTid(String targetSystem,
                                          String tid,
                                          String deviceName,
                                          String alid,
                                          String alidDescEn,
                                          String alidDescCh,
                                          String alarmCode) {
        String useTid = pickTid(tid);
        try {
            S007CommandPayload payload = new S007CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S007");
            payload.setIdDesc("ALARM");
            payload.setTid(useTid);

            S007CommandPayload.Message msg = new S007CommandPayload.Message();
            msg.setDeviceName(deviceName);
            msg.setAlid(alid);
            msg.setAlidDescEn(alidDescEn);
            msg.setAlidDescCh(alidDescCh);
            msg.setAlarmCode(alarmCode);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S007", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] S007 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S007 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S008 警告事件通知
     * - requireAck：false
     */
    public MqttSendResult sendS008(String targetSystem,
                                   String deviceName,
                                   String alid,
                                   String alidDescEn,
                                   String alidDescCh,
                                   String alarmCode) {
        return sendS008WithTid(targetSystem, null, deviceName, alid, alidDescEn, alidDescCh, alarmCode);
    }

    public MqttSendResult sendS008WithTid(String targetSystem,
                                          String tid,
                                          String deviceName,
                                          String alid,
                                          String alidDescEn,
                                          String alidDescCh,
                                          String alarmCode) {
        String useTid = pickTid(tid);
        try {
            S008CommandPayload payload = new S008CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S008");
            payload.setIdDesc("WARNING");
            payload.setTid(useTid);

            S008CommandPayload.Message msg = new S008CommandPayload.Message();
            msg.setDeviceName(deviceName);
            msg.setAlid(alid);
            msg.setAlidDescEn(alidDescEn);
            msg.setAlidDescCh(alidDescCh);
            msg.setAlarmCode(alarmCode);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S008", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] S008 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S008 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S010 刷卡驗證請求
     * - requireAck：true
     */
    public MqttSendResult sendS010(String targetSystem, String cardNumber, String deviceName, String safeDoorName) {
        return sendS010WithTid(targetSystem, null, cardNumber, deviceName, safeDoorName);
    }

    public MqttSendResult sendS010WithTid(String targetSystem, String tid,
                                          String cardNumber, String deviceName, String safeDoorName) {
        String useTid = pickTid(tid);
        try {
            S010CommandPayload payload = new S010CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S010");
            payload.setIdDesc("CARD_NUMBER_CHECK");
            payload.setTid(useTid);

            S010CommandPayload.Message msg = new S010CommandPayload.Message();
            msg.setCardNumber(cardNumber);
            msg.setDeviceName(deviceName);
            msg.setSafeDoorName(safeDoorName);
            payload.setMessage(msg);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S010", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S010 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S010 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S011 開門資格驗證
     * - requireAck：true
     */
    public MqttSendResult sendS011(String targetSystem, String deviceName, String safeDoorName) {
        return sendS011WithTid(targetSystem, null, deviceName, safeDoorName);
    }

    public MqttSendResult sendS011WithTid(String targetSystem, String tid, String deviceName, String safeDoorName) {
        String useTid = pickTid(tid);
        try {
            S011CommandPayload payload = new S011CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S011");
            payload.setIdDesc("OPEN_DOOR_CHECK");
            payload.setTid(useTid);

            S011CommandPayload.Message msg = new S011CommandPayload.Message();
            msg.setDeviceName(deviceName);
            msg.setSafeDoorName(safeDoorName);
            payload.setMessage(msg);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S011", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S011 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S011 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S012 關門資格驗證
     * - requireAck：true
     */
    public MqttSendResult sendS012(String targetSystem, String deviceName, String safeDoorName) {
        return sendS012WithTid(targetSystem, null, deviceName, safeDoorName);
    }

    public MqttSendResult sendS012WithTid(String targetSystem, String tid, String deviceName, String safeDoorName) {
        String useTid = pickTid(tid);
        try {
            S012CommandPayload payload = new S012CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S012");
            payload.setIdDesc("CLOSE_DOOR_CHECK");
            payload.setTid(useTid);

            S012CommandPayload.Message msg = new S012CommandPayload.Message();
            msg.setDeviceName(deviceName);
            msg.setSafeDoorName(safeDoorName);
            payload.setMessage(msg);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S012", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S012 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S012 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S013 RESET/START 驗證
     * - requireAck：true
     */
    public MqttSendResult sendS013(String targetSystem, String deviceName) {
        return sendS013WithTid(targetSystem, null, deviceName);
    }

    public MqttSendResult sendS013WithTid(String targetSystem, String tid, String deviceName) {
        String useTid = pickTid(tid);
        try {
            S013CommandPayload payload = new S013CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S013");
            payload.setIdDesc("RESET_CHECK");
            payload.setTid(useTid);

            S013CommandPayload.Message msg = new S013CommandPayload.Message();
            msg.setDeviceName(deviceName);
            payload.setMessage(msg);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S013", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S013 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S013 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S014 零件預警清單
     * - requireAck：true
     */
    public MqttSendResult sendS014(String targetSystem) {
        return sendS014WithTid(targetSystem, null);
    }

    public MqttSendResult sendS014WithTid(String targetSystem, String tid) {
        String useTid = pickTid(tid);
        try {
            S014CommandPayload payload = new S014CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S014");
            payload.setIdDesc("TOOL_REMIND_LIST");
            payload.setTid(useTid);
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S014", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S014 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S014 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S015 零件預警設定
     * - requireAck：true
     */
    public MqttSendResult sendS015(String targetSystem, List<S015CommandPayload.ToolSetting> toolSettings) {
        return sendS015WithTid(targetSystem, null, toolSettings);
    }

    public MqttSendResult sendS015WithTid(String targetSystem, String tid,
                                          List<S015CommandPayload.ToolSetting> toolSettings) {
        String useTid = pickTid(tid);
        try {
            S015CommandPayload payload = new S015CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S015");
            payload.setIdDesc("TOOL_REMIND_SETTING");
            payload.setTid(useTid);

            S015CommandPayload.Message msg = new S015CommandPayload.Message();
            msg.setToolList(toolSettings);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S015", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S015 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S015 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S016 系統校時
     * - requireAck：true（如單向下發可改 false）
     */
    public MqttSendResult sendS016(String targetSystem, String datetime) {
        return sendS016WithTid(targetSystem, null, datetime);
    }

    public MqttSendResult sendS016WithTid(String targetSystem, String tid, String datetime) {
        String useTid = pickTid(tid);
        try {
            S016CommandPayload payload = new S016CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S016");
            payload.setIdDesc("SYSTEM_TIMING");
            payload.setTid(useTid);

            S016CommandPayload.Message msg = new S016CommandPayload.Message();
            msg.setDatetime(datetime);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S016", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S016 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S016 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S020（通用版）
     * - 傳入完整的 Message（可帶任意你定義過的欄位）
     * - requireAck：false
     */
    public MqttSendResult sendS020Generic(String targetSystem, S020CommandPayload.Message message) {
        return sendS020GenericWithTid(targetSystem, null, message);
    }

    public MqttSendResult sendS020GenericWithTid(String targetSystem, String tid, S020CommandPayload.Message message) {
        String useTid = pickTid(tid);
        try {
            S020CommandPayload payload = S020CommandPayload.of(useTid, message);
            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S020", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] S020(Generic) 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S020(Generic) 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * S020-2001：入庫完成
     * 一般會帶：1D_BARCODE、LOT_ID、CARRIERID、TYPE、WIPNAME、NUM
     */
    public MqttSendResult sendS020_2001_StockIn(String targetSystem,
                                                String oneDBarcode, String lotId, String carrierId,
                                                String type, String wipname, String num) {
        return sendS020_2001_StockInWithTid(targetSystem, null, oneDBarcode, lotId, carrierId, type, wipname, num);
    }

    public MqttSendResult sendS020_2001_StockInWithTid(String targetSystem, String tid,
                                                       String oneDBarcode, String lotId, String carrierId,
                                                       String type, String wipname, String num) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.Message.of2001(oneDBarcode, lotId, carrierId, type, wipname, num));
    }

    /**
     * S020-2002：出庫完成
     * 一般會帶：1D_BARCODE、LOT_ID、CARRIERID、TYPE、WIPNAME、NUM
     */
    public MqttSendResult sendS020_2002_StockOut(String targetSystem,
                                                 String oneDBarcode, String lotId, String carrierId,
                                                 String type, String wipname, String num) {
        return sendS020_2002_StockOutWithTid(targetSystem, null, oneDBarcode, lotId, carrierId, type, wipname, num);
    }

    public MqttSendResult sendS020_2002_StockOutWithTid(String targetSystem, String tid,
                                                        String oneDBarcode, String lotId, String carrierId,
                                                        String type, String wipname, String num) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.Message.of2002(oneDBarcode, lotId, carrierId, type, wipname, num));
    }

    /**
     * S020-2004：產品放到貨架
     * 一般會帶：LOT_ID、CARRIERID、TYPE、WIPNAME、NUM
     */
    public MqttSendResult sendS020_2004_ProductPutOnShelf(String targetSystem,
                                                          String lotId, String carrierId,
                                                          String type, String wipname, String num) {
        return sendS020_2004_ProductPutOnShelfWithTid(targetSystem, null, lotId, carrierId, type, wipname, num);
    }

    public MqttSendResult sendS020_2004_ProductPutOnShelfWithTid(String targetSystem, String tid,
                                                                 String lotId, String carrierId,
                                                                 String type, String wipname, String num) {
        return sendS020GenericWithTid(
                targetSystem,
                tid,
                S020CommandPayload.Message.of2004(lotId, carrierId, type, wipname, num)
        );
    }

    /**
     * S020-2005：產品離開貨架
     * 一般會帶：LOT_ID、CARRIERID、TYPE、WIPNAME、NUM
     */
    public MqttSendResult sendS020_2005_ProductLeaveShelf(String targetSystem,
                                                          String lotId, String carrierId,
                                                          String type, String wipname, String num) {
        return sendS020_2005_ProductLeaveShelfWithTid(targetSystem, null, lotId, carrierId, type, wipname, num);
    }

    public MqttSendResult sendS020_2005_ProductLeaveShelfWithTid(String targetSystem, String tid,
                                                                 String lotId, String carrierId,
                                                                 String type, String wipname, String num) {
        return sendS020GenericWithTid(
                targetSystem,
                tid,
                S020CommandPayload.Message.of2005(lotId, carrierId, type, wipname, num)
        );
    }

    /**
     * S020-2008：手動清除
     * 一般會帶：LOT_ID、CARRIERID、TYPE、WIPNAME、NUM
     */
    public MqttSendResult sendS020_2008_ProductLeaveShelf(String targetSystem,
                                                          String barcode, String lotId, String carrierId,
                                                          String type, String wipname, String num) {
        return sendS020_2008_ProductLeaveShelfWithTid(targetSystem, barcode, null, lotId, carrierId, type, wipname, num);
    }

    public MqttSendResult sendS020_2008_ProductLeaveShelfWithTid(String targetSystem, String barcode, String tid,
                                                                 String lotId, String carrierId,
                                                                 String type, String wipname, String num) {
        return sendS020GenericWithTid(
                targetSystem,
                tid,
                S020CommandPayload.Message.of2008(barcode, lotId, carrierId, type, wipname, num)
        );
    }

    /**
     * S020：Port 類狀態變更（1010/1011/1012/1014/1016/1017...）
     * 需要帶：STATUS（必），LOT_ID/CARRIERID 視情況帶
     *
     * @param ceid       例如："1010"
     * @param ceidDescCh 例如："ManualPort01狀態變更"
     */
    public MqttSendResult sendS020_PortStatusChange(String targetSystem,
                                                    String ceid, String ceidDescCh,
                                                    String status, String lotId, String carrierId) {
        return sendS020_PortStatusChangeWithTid(targetSystem, null, ceid, ceidDescCh, status, lotId, carrierId);
    }

    public MqttSendResult sendS020_PortStatusChangeWithTid(String targetSystem, String tid,
                                                           String ceid, String ceidDescCh,
                                                           String status, String lotId, String carrierId) {
        var message = S020CommandPayload.Message.ofPortStatus(ceid, ceidDescCh, status, lotId, carrierId);
        return sendS020GenericWithTid(targetSystem, tid, message);
    }

    /**
     * S020：設備/STK 狀態變更（1004/1005/...）
     *
     * @param ceid       例如："1004"
     * @param ceidDescCh 例如："STK狀態變更"、"拆併機狀態變更"
     * @param status     例如："Executing"、"Idle"
     */
    public MqttSendResult sendS020_DeviceStatusChange(String targetSystem,
                                                      String ceid, String ceidDescCh, String status) {
        return sendS020_DeviceStatusChangeWithTid(targetSystem, null, ceid, ceidDescCh, status);
    }

    public MqttSendResult sendS020_DeviceStatusChangeWithTid(String targetSystem, String tid,
                                                             String ceid, String ceidDescCh, String status) {
        var message = S020CommandPayload.Message.ofDeviceStatus(ceid, ceidDescCh, status);
        return sendS020GenericWithTid(targetSystem, tid, message);
    }

    /**
     * S020-2003：拆/併打帶完成，等待標籤資訊
     * 需要帶：LOT_ID、CARRIERID
     * - requireAck：false
     */
    public MqttSendResult sendS020_2003_TagWait(String targetSystem, String lotId, String carrierId) {
        return sendS020_2003_TagWaitWithTid(targetSystem, null, lotId, carrierId);
    }

    public MqttSendResult sendS020_2003_TagWaitWithTid(String targetSystem, String tid,
                                                       String lotId, String carrierId) {
        return sendS020GenericWithTid(targetSystem, tid, S020CommandPayload.Message.of2003(lotId, carrierId));
    }

    /* ===================================================================== */
    /* S020-3001~3009：ZIPA 事件                                              */
    /* ===================================================================== */

    /**
     * S020-3001：ZIPA 入料詢問
     */
    public MqttSendResult sendS020_3001_ZipaInboundRequest(String targetSystem,
                                                           String barcode) {
        return sendS020_3001_ZipaInboundRequestWithTid(targetSystem, null, barcode);
    }

    public MqttSendResult sendS020_3001_ZipaInboundRequestWithTid(String targetSystem, String tid,
                                                                  String barcode) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3001(barcode));
    }

    /**
     * S020-3002：ZIPA 入倉輸送中
     */
    public MqttSendResult sendS020_3002_ZipaInboundTransfer(String targetSystem,
                                                            String barcode, String carrierId,
                                                            String lotId, String trayHigh, String trayType) {
        return sendS020_3002_ZipaInboundTransferWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3002_ZipaInboundTransferWithTid(String targetSystem, String tid,
                                                                   String barcode, String carrierId,
                                                                   String lotId, String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3002(barcode, carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3003：ZIPA 入倉搬運中(手臂)
     */
    public MqttSendResult sendS020_3003_ZipaInboundHandlingArm(String targetSystem,
                                                               String barcode, String carrierId,
                                                               String lotId, String trayHigh, String trayType) {
        return sendS020_3003_ZipaInboundHandlingArmWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3003_ZipaInboundHandlingArmWithTid(String targetSystem, String tid,
                                                                      String barcode, String carrierId,
                                                                      String lotId, String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3003(barcode, carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3004：ZIPA 上架
     */
    public MqttSendResult sendS020_3004_ZipaStorageComplete(String targetSystem,
                                                            String barcode, String carrierId,
                                                            String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020_3004_ZipaStorageCompleteWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3004_ZipaStorageCompleteWithTid(String targetSystem, String tid,
                                                                   String barcode, String carrierId,
                                                                   String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3004(barcode, carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3005：ZIPA 出倉搬運中(手臂)
     */
    public MqttSendResult sendS020_3005_ZipaOutboundHandlingArm(String targetSystem,
                                                                String barcode, String carrierId,
                                                                String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020_3005_ZipaOutboundHandlingArmWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3005_ZipaOutboundHandlingArmWithTid(String targetSystem, String tid,
                                                                       String barcode, String carrierId,
                                                                       String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3005(barcode, carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3006：ZIPA 出倉
     */
    public MqttSendResult sendS020_3006_ZipaOutboundComplete(String targetSystem,
                                                             String barcode, String carrierId,
                                                             String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020_3006_ZipaOutboundCompleteWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3006_ZipaOutboundCompleteWithTid(String targetSystem, String tid,
                                                                    String barcode, String carrierId,
                                                                    String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3006(barcode, carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3007：ZIPA 出庫輸送中
     */
    public MqttSendResult sendS020_3007_ZipaOutboundTransfer(String targetSystem,
                                                             String barcode, String carrierId,
                                                             String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020_3007_ZipaOutboundTransferWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3007_ZipaOutboundTransferWithTid(String targetSystem, String tid,
                                                                    String barcode, String carrierId,
                                                                    String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3007(barcode, carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3008：ZIPA 入庫失敗
     */
    public MqttSendResult sendS020_3008_ZipaInboundFailed(String targetSystem,
                                                          String barcode) {
        return sendS020_3008_ZipaInboundFailedWithTid(targetSystem, null, barcode);
    }

    public MqttSendResult sendS020_3008_ZipaInboundFailedWithTid(String targetSystem, String tid,
                                                                 String barcode) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3008(barcode));
    }

    /**
     * S020-3009：ZIPA 出庫失敗
     */
    public MqttSendResult sendS020_3009_ZipaOutboundFailed(String targetSystem,
                                                           String barcode, String carrierId,
                                                           String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020_3009_ZipaOutboundFailedWithTid(targetSystem, null,
                barcode, carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3009_ZipaOutboundFailedWithTid(String targetSystem, String tid,
                                                                  String barcode, String carrierId,
                                                                  String lotId, String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3009(barcode, carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /* ===================================================================== */
    /* S020-3101~3107：WIP 事件                                              */
    /* ===================================================================== */

    /**
     * S020-3101：WIP 入倉輸送中
     */
    public MqttSendResult sendS020_3101_WipInboundTransfer(String targetSystem,
                                                           String carrierId, String lotId,
                                                           String trayHigh, String trayType) {
        return sendS020_3101_WipInboundTransferWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3101_WipInboundTransferWithTid(String targetSystem, String tid,
                                                                  String carrierId, String lotId,
                                                                  String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3101(carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3102：WIP 入倉搬運中(手臂)
     */
    public MqttSendResult sendS020_3102_WipInboundHandlingArm(String targetSystem,
                                                              String carrierId, String lotId,
                                                              String trayHigh, String trayType) {
        return sendS020_3102_WipInboundHandlingArmWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3102_WipInboundHandlingArmWithTid(String targetSystem, String tid,
                                                                     String carrierId, String lotId,
                                                                     String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3102(carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3103：WIP 上架
     */
    public MqttSendResult sendS020_3103_WipStorageComplete(String targetSystem,
                                                           String carrierId, String lotId,
                                                           String trayHigh, String trayType, String trayNum) {
        return sendS020_3103_WipStorageCompleteWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3103_WipStorageCompleteWithTid(String targetSystem, String tid,
                                                                  String carrierId, String lotId,
                                                                  String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3103(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3104：WIP 出倉搬運中(手臂)
     */
    public MqttSendResult sendS020_3104_WipOutboundHandlingArm(String targetSystem,
                                                               String carrierId, String lotId,
                                                               String trayHigh, String trayType, String trayNum) {
        return sendS020_3104_WipOutboundHandlingArmWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3104_WipOutboundHandlingArmWithTid(String targetSystem, String tid,
                                                                      String carrierId, String lotId,
                                                                      String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3104(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3105：WIP 出倉
     */
    public MqttSendResult sendS020_3105_WipOutboundComplete(String targetSystem,
                                                            String carrierId, String lotId,
                                                            String trayHigh, String trayType, String trayNum) {
        return sendS020_3105_WipOutboundCompleteWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3105_WipOutboundCompleteWithTid(String targetSystem, String tid,
                                                                   String carrierId, String lotId,
                                                                   String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3105(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3106：WIP 入庫失敗
     */
    public MqttSendResult sendS020_3106_WipInboundFailed(String targetSystem,
                                                         String carrierId, String lotId,
                                                         String trayHigh, String trayType) {
        return sendS020_3106_WipInboundFailedWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3106_WipInboundFailedWithTid(String targetSystem, String tid,
                                                                String carrierId, String lotId,
                                                                String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3106(carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3107：WIP 出庫失敗
     */
    public MqttSendResult sendS020_3107_WipOutboundFailed(String targetSystem,
                                                          String carrierId, String lotId,
                                                          String trayHigh, String trayType, String trayNum) {
        return sendS020_3107_WipOutboundFailedWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3107_WipOutboundFailedWithTid(String targetSystem, String tid,
                                                                 String carrierId, String lotId,
                                                                 String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3107(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /* ===================================================================== */
    /* S020-3201~3203：拆併 站事件                                           */
    /* ===================================================================== */

    /**
     * S020-3201：拆併 OCR 檢測
     */
    public MqttSendResult sendS020_3201_DismantleOcrCheck(String targetSystem,
                                                          String carrierId, String lotId,
                                                          String trayHigh, String trayType, String trayNum) {
        return sendS020_3201_DismantleOcrCheckWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3201_DismantleOcrCheckWithTid(String targetSystem, String tid,
                                                                 String carrierId, String lotId,
                                                                 String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3201(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3202：拆併 異物 檢測
     */
    public MqttSendResult sendS020_3202_DismantleForeignObjectCheck(String targetSystem,
                                                                    String carrierId, String lotId,
                                                                    String trayHigh, String trayType, String trayNum) {
        return sendS020_3202_DismantleForeignObjectCheckWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3202_DismantleForeignObjectCheckWithTid(String targetSystem, String tid,
                                                                           String carrierId, String lotId,
                                                                           String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3202(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3203：拆併 打帶完成，等待標籤資訊
     */
    public MqttSendResult sendS020_3203_DismantleStrappingDoneWaitingLabel(String targetSystem,
                                                                           String carrierId, String lotId,
                                                                           String trayHigh, String trayType, String trayNum) {
        return sendS020_3203_DismantleStrappingDoneWaitingLabelWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3203_DismantleStrappingDoneWaitingLabelWithTid(String targetSystem, String tid,
                                                                                  String carrierId, String lotId,
                                                                                  String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3203(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /* ===================================================================== */
    /* S020-3301~3305：ZIPB 事件                                             */
    /* ===================================================================== */

    /**
     * S020-3301：ZIPB 入料詢問
     */
    public MqttSendResult sendS020_3301_ZipbInboundRequest(String targetSystem,
                                                           String carrierId, String lotId,
                                                           String trayHigh, String trayType, String trayNum) {
        return sendS020_3301_ZipbInboundRequestWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3301_ZipbInboundRequestWithTid(String targetSystem, String tid,
                                                                  String carrierId, String lotId,
                                                                  String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3301(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3302：ZIPB 入倉搬運中(手臂)
     */
    public MqttSendResult sendS020_3302_ZipbInboundHandlingArm(String targetSystem,
                                                               String carrierId, String lotId,
                                                               String trayHigh, String trayType) {
        return sendS020_3302_ZipbInboundHandlingArmWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType);
    }

    public MqttSendResult sendS020_3302_ZipbInboundHandlingArmWithTid(String targetSystem, String tid,
                                                                      String carrierId, String lotId,
                                                                      String trayHigh, String trayType) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3302(carrierId, lotId, trayHigh, trayType));
    }

    /**
     * S020-3303：ZIPB 上架
     */
    public MqttSendResult sendS020_3303_ZipbStorageComplete(String targetSystem,
                                                            String carrierId, String lotId,
                                                            String trayHigh, String trayType, String trayNum) {
        return sendS020_3303_ZipbStorageCompleteWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3303_ZipbStorageCompleteWithTid(String targetSystem, String tid,
                                                                   String carrierId, String lotId,
                                                                   String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3303(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3304：ZIPB 出倉
     */
    public MqttSendResult sendS020_3304_ZipbOutboundComplete(String targetSystem,
                                                             String carrierId, String lotId,
                                                             String trayHigh, String trayType, String trayNum) {
        return sendS020_3304_ZipbOutboundCompleteWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3304_ZipbOutboundCompleteWithTid(String targetSystem, String tid,
                                                                    String carrierId, String lotId,
                                                                    String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3304(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * S020-3305：ZIPB 入庫失敗
     */
    public MqttSendResult sendS020_3305_ZipbInboundFailed(String targetSystem,
                                                          String carrierId, String lotId,
                                                          String trayHigh, String trayType, String trayNum) {
        return sendS020_3305_ZipbInboundFailedWithTid(targetSystem, null,
                carrierId, lotId, trayHigh, trayType, trayNum);
    }

    public MqttSendResult sendS020_3305_ZipbInboundFailedWithTid(String targetSystem, String tid,
                                                                 String carrierId, String lotId,
                                                                 String trayHigh, String trayType, String trayNum) {
        return sendS020GenericWithTid(targetSystem, tid,
                S020CommandPayload.of3305(carrierId, lotId, trayHigh, trayType, trayNum));
    }

    /**
     * 發送 S065 標籤資訊印製（格式一）
     * 舊簽名保留：將 lotId 映射為 TagInfo.SCH；carrierId 目前無對應欄位（示範保留）
     * - requireAck：true
     * - ID_DESC：TAG_INFO
     */
    public MqttSendResult sendS065(String targetSystem, String lotId, String carrierId) {
        return sendS065WithTid(targetSystem, null, lotId, carrierId);
    }

    public MqttSendResult sendS065WithTid(String targetSystem, String tid, String lotId, String carrierId) {
        String useTid = pickTid(tid);
        try {
            S065CommandPayload payload = new S065CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S065");
            payload.setIdDesc("TAG_INFO");
            payload.setTid(useTid);

            S065CommandPayload.TagInfo tag = new S065CommandPayload.TagInfo();
            tag.setSch(carrierId);
            tag.setQty("100");
            tag.setPass("80");
            tag.setBga("10");
            tag.setBbi("1");
            tag.setMark("1");
            tag.setTpi("8");

            payload.setMessage(Collections.singletonList(tag));
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S065", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S065 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S065 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S067 電池資訊回拋
     * - requireAck：true
     */
    public MqttSendResult sendS067(String targetSystem) {
        return sendS067WithTid(targetSystem, null);
    }

    public MqttSendResult sendS067WithTid(String targetSystem, String tid) {
        String useTid = pickTid(tid);
        try {
            S067CommandPayload payload = new S067CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S067");
            payload.setIdDesc("BATTERY_STATUS");
            payload.setTid(useTid);
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S067", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S067 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S067 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S068 打帶前狀態確認
     * - requireAck：true
     */
    public MqttSendResult sendS068(String targetSystem) {
        return sendS068WithTid(targetSystem, null);
    }

    public MqttSendResult sendS068WithTid(String targetSystem, String tid) {
        String useTid = pickTid(tid);
        try {
            S068CommandPayload payload = new S068CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S068");
            payload.setIdDesc("TAPING_MACHINE_CHECK");
            payload.setTid(useTid);
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S068", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S068 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S068 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S072 Tray 間隙檢查（左右影像）
     * - requireAck：true
     */
    public MqttSendResult sendS072(String targetSystem,
                                   String carrierId,
                                   String lotId,
                                   String trayType,
                                   byte[] trayLeftImage,
                                   byte[] trayRightImage) {
        S072CommandPayload.Message msg = new S072CommandPayload.Message();
        msg.setCarrierId(carrierId);
        msg.setLotId(lotId);
        msg.setTrayType(trayType);
        msg.setTrayLeftImage(trayLeftImage);
        msg.setTrayRightImage(trayRightImage);
        return sendS072WithTid(targetSystem, null, msg);
    }

    /**
     * 發送 S072（指定 TID）
     * - CMD: SYSTEM
     * - CMD_ID: S072
     * - ID_DESC: TRAY_GAP_CHECK
     * - requireAck：true
     */
    public MqttSendResult sendS072WithTid(String targetSystem,
                                          String tid,
                                          S072CommandPayload.Message message) {
        String useTid = pickTid(tid);
        try {
            S072CommandPayload payload = new S072CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S072");
            payload.setIdDesc("TRAY_GAP_CHECK");
            payload.setTid(useTid);

            // Message（需已塞好：barcode、carrierId、lotId、trayType、left/right image）
            payload.setMessage(message);

            // 送出時 RESULT 空白
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S072", json, useTid, /*requireAck*/ true);
        } catch (Exception e) {
            log.error("[MQTT] S072 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S072 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 S073 拆併前 Tray 圖像確認
     * - requireAck：true
     */
    public MqttSendResult sendS073(String targetSystem,
                                   String lotId,
                                   String trayType,
                                   String trayDesc,
                                   S073CommandPayload.Message message) {
        return sendS073WithTid(targetSystem, null, lotId, trayType, trayDesc, message);
    }

    public MqttSendResult sendS073WithTid(String targetSystem, String tid,
                                          String lotId, String trayType, String trayDesc,
                                          S073CommandPayload.Message message) {
        String useTid = pickTid(tid);
        try {
            S073CommandPayload payload = new S073CommandPayload();
            payload.setCmd("SYSTEM");
            payload.setCmdId("S073");
            payload.setIdDesc("TRAY_OCR_CHECK_INFO");
            payload.setTid(useTid);

            message.setLotId(lotId);
            message.setTrayType(trayType);
            message.setTrayDesc(trayDesc);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "S073", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] S073 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("S073 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 L005 條碼檢查（入 STK）
     * - requireAck：true
     */
    public MqttSendResult sendL005(String targetSystem, String barcode) {
        return sendL005WithTid(targetSystem, null, barcode);
    }

    public MqttSendResult sendL005WithTid(String targetSystem, String tid, String barcode) {
        String useTid = pickTid(tid);
        try {
            L005CommandPayload payload = new L005CommandPayload();
            payload.setCmd("LOAD");
            payload.setCmdId("L005");
            payload.setIdDesc("BARCODE_CHECK_EVENT");
            payload.setTid(useTid);

            L005CommandPayload.Message msg = new L005CommandPayload.Message();
            msg.setBarcode(barcode);
            payload.setMessage(msg);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "L005", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] L005 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("L005 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 R007 任務指令（WIP → EQP）
     * - requireAck：true
     */
    public MqttSendResult sendR007(String targetSystem,
                                   String lotId,
                                   String carrierId,
                                   String wipName,
                                   String destLoc,
                                   String eqpPort,
                                   String deviceName,
                                   String stkPort,
                                   Integer movePriority) {
        return sendR007WithTid(targetSystem, null, lotId, carrierId, wipName, destLoc, eqpPort, deviceName, stkPort, movePriority);
    }

    public MqttSendResult sendR007WithTid(String targetSystem, String tid,
                                          String lotId, String carrierId, String wipName,
                                          String destLoc, String eqpPort, String deviceName,
                                          String stkPort, Integer movePriority) {
        String useTid = pickTid(tid);
        try {
            R007CommandPayload payload = new R007CommandPayload();
            payload.setCmd("ROBOT");
            payload.setCmdId("R007");
            payload.setIdDesc("ROBOT_MOVE_SCH_TO_EQP");
            payload.setTid(useTid);

            R007CommandPayload.Message message = new R007CommandPayload.Message();
            message.setLotId(lotId);
            message.setCarrierId(carrierId);
            message.setWipName(wipName);
            message.setDestLoc(destLoc);
            message.setEqpPort(eqpPort);
            message.setDeviceName(deviceName);
            message.setStkPort(stkPort);
            payload.setMessage(message);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "R007", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] R007 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("R007 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 R008 任務指令（EQP → WIP/STK）
     * - requireAck：true
     */
    public MqttSendResult sendR008(String targetSystem,
                                   String lotId,
                                   String carrierId,
                                   String wipName,
                                   String destLoc,
                                   String eqpPort,
                                   String deviceName,
                                   String stkPort,
                                   Integer movePriority) {
        return sendR008WithTid(targetSystem, null, lotId, carrierId, wipName, destLoc, eqpPort, deviceName, stkPort, movePriority);
    }

    public MqttSendResult sendR008WithTid(String targetSystem, String tid,
                                          String lotId, String carrierId, String wipName,
                                          String destLoc, String eqpPort, String deviceName,
                                          String stkPort, Integer movePriority) {
        String useTid = pickTid(tid);
        try {
            R008CommandPayload payload = new R008CommandPayload();
            payload.setCmd("ROBOT");
            payload.setCmdId("R008");
            payload.setIdDesc("ROBOT_MOVE_SCH_TO_WIP");
            payload.setTid(useTid);

            R008CommandPayload.Message message = new R008CommandPayload.Message();
            message.setLotId(lotId);
            message.setCarrierId(carrierId);
            message.setWipName(wipName);
            message.setDestLoc(destLoc);
            message.setEqpPort(eqpPort);
            message.setDeviceName(deviceName);
            message.setStkPort(stkPort);
            payload.setMessage(message);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "R008", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] R008 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("R008 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 R018 刪除任務
     * - requireAck：true
     */
    public MqttSendResult sendR018(String targetSystem, String cmdTid) {
        return sendR018WithTid(targetSystem, null, cmdTid);
    }

    public MqttSendResult sendR018WithTid(String targetSystem, String tid, String cmdTid) {
        String useTid = pickTid(tid);
        try {
            R018CommandPayload payload = new R018CommandPayload();
            payload.setCmd("ROBOT");
            payload.setCmdId("R018");
            payload.setIdDesc("DELETE_TASK");
            payload.setTid(useTid);

            R018CommandPayload.Message msg = new R018CommandPayload.Message();
            msg.setCmdTid(cmdTid);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "R018", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] R018 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("R018 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 R030 E-Rack → EQP
     * - requireAck：true
     */
    public MqttSendResult sendR030(String targetSystem,
                                   String lotId,
                                   String carrierId,
                                   String wipName,
                                   String destLoc,
                                   String eqpPort,
                                   String deviceName,
                                   String stkPort,
                                   String agvSpeed,
                                   String armSpeed) {
        return sendR030WithTid(targetSystem, null, lotId, carrierId, wipName, destLoc, eqpPort, deviceName, stkPort, agvSpeed, armSpeed);
    }

    public MqttSendResult sendR030WithTid(String targetSystem, String tid,
                                          String lotId, String carrierId, String wipName,
                                          String destLoc, String eqpPort, String deviceName,
                                          String stkPort, String agvSpeed, String armSpeed) {
        String useTid = pickTid(tid);
        try {
            R030CommandPayload payload = new R030CommandPayload();
            payload.setCmd("ROBOT");
            payload.setCmdId("R030");
            payload.setIdDesc("ROBOT_MOVE_ERACK_TO_EQP");
            payload.setTid(useTid);

            R030CommandPayload.Message message = new R030CommandPayload.Message();
            message.setLotId(lotId);
            message.setCarrierId(carrierId);
            message.setWipName(wipName);
            message.setDestLoc(destLoc);
            message.setEqpPort(eqpPort);
            message.setDeviceName(deviceName);
            message.setStkPort(stkPort);
            message.setAgvSpeed(agvSpeed);
            message.setRoboticArmSpeed(armSpeed);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "R030", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] R030 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("R030 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A008 AGV 車事件
     * - requireAck：false（純上報）
     */
    public MqttSendResult sendA008(String targetSystem,
                                   String deviceName,
                                   String status,
                                   String commandId,
                                   String carrierId,
                                   String battery,
                                   String destLoc,
                                   String jobStatus) {
        return sendA008WithTid(targetSystem, null, deviceName, status, commandId, carrierId, battery, destLoc, jobStatus);
    }

    public MqttSendResult sendA008WithTid(String targetSystem, String tid,
                                          String deviceName, String status, String commandId, String carrierId,
                                          String battery, String destLoc, String jobStatus) {
        String useTid = pickTid(tid);
        try {
            A008CommandPayload payload = new A008CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A008");
            payload.setIdDesc("AGV EVENT");
            payload.setTid(useTid);

            A008CommandPayload.Message message = new A008CommandPayload.Message();
            message.setDeviceName(deviceName);
            message.setStatus(status);
            message.setCommandId(commandId);
            message.setCarrierId(carrierId);
            message.setBattery(battery);
            message.setDestLoc(destLoc);
            message.setJobStatus(jobStatus);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A008", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] A008 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A008 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A009 詢問 AGV 狀態
     * - requireAck：true
     */
    public MqttSendResult sendA009(String targetSystem) {
        return sendA009WithTid(targetSystem, null);
    }

    public MqttSendResult sendA009WithTid(String targetSystem, String tid) {
        String useTid = pickTid(tid);
        try {
            A009CommandPayload payload = new A009CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A009");
            payload.setIdDesc("AGV STATUS");
            payload.setTid(useTid);
            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A009", json, useTid, true);
        } catch (Exception e) {
            log.error("[MQTT] A009 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A009 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A010 全量 AGV 狀態回拋
     * - requireAck：false（高頻回拋）
     */
    public MqttSendResult sendA010(String targetSystem, List<A010CommandPayload.AgvStatus> agvStatusList) {
        return sendA010WithTid(targetSystem, null, agvStatusList);
    }

    public MqttSendResult sendA010WithTid(String targetSystem, String tid, List<A010CommandPayload.AgvStatus> agvStatusList) {
        String useTid = pickTid(tid);
        try {
            A010CommandPayload payload = new A010CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A010");
            payload.setIdDesc("ALL AGV STATUS REPLAY");
            payload.setTid(useTid);
            payload.setReplay("ACTION");

            A010CommandPayload.Message message = new A010CommandPayload.Message();
            message.setData(agvStatusList);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A010", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] A010 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A010 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A013 AGV 離開換電站
     * - requireAck：false（純上報）
     */
    public MqttSendResult sendA013(String targetSystem,
                                   String deviceName,
                                   String batteryId,
                                   String batteryValue,
                                   String odo,
                                   String trip) {
        return sendA013WithTid(targetSystem, null, deviceName, batteryId, batteryValue, odo, trip);
    }

    public MqttSendResult sendA013WithTid(String targetSystem, String tid,
                                          String deviceName, String batteryId, String batteryValue, String odo, String trip) {
        String useTid = pickTid(tid);
        try {
            A013CommandPayload payload = new A013CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A013");
            payload.setIdDesc("AGV_LEAVE_POWER_STATION");
            payload.setTid(useTid);

            A013CommandPayload.Message message = new A013CommandPayload.Message();
            message.setDeviceName(deviceName);
            message.setBatteryId(batteryId);
            message.setBatteryValue(batteryValue);
            message.setOdo(odo);
            message.setTrip(trip);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A013", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] A013 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A013 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A014 AGV 回到換電站
     * - requireAck：false（純上報）
     */
    public MqttSendResult sendA014(String targetSystem,
                                   String deviceName,
                                   String batteryId,
                                   String batteryValue,
                                   String odo,
                                   String trip) {
        return sendA014WithTid(targetSystem, null, deviceName, batteryId, batteryValue, odo, trip);
    }

    public MqttSendResult sendA014WithTid(String targetSystem, String tid,
                                          String deviceName, String batteryId, String batteryValue, String odo, String trip) {
        String useTid = pickTid(tid);
        try {
            A014CommandPayload payload = new A014CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A014");
            payload.setIdDesc("AGV_ARRIVAL_POWER_STATION");
            payload.setTid(useTid);

            A014CommandPayload.Message message = new A014CommandPayload.Message();
            message.setDeviceName(deviceName);
            message.setBatteryId(batteryId);
            message.setBatteryValue(batteryValue);
            message.setOdo(odo);
            message.setTrip(trip);
            payload.setMessage(message);

            payload.setResult("");
            payload.setResultMessage("");

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A014", json, useTid, false);
        } catch (Exception e) {
            log.error("[MQTT] A014 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A014 指令發送失敗：" + e.getMessage(), useTid);
        }
    }

    /**
     * 發送 A015 AGV 抵達 EQP
     * - 注意：此方法沿用外部傳入的「原始任務 TID」
     * - requireAck：true（如僅事件上報可改 false）
     */
    public MqttSendResult sendA015(String targetSystem,
                                   String tid,
                                   String deviceName,
                                   String destLoc) {
        try {
            A015CommandPayload payload = new A015CommandPayload();
            payload.setCmd("AGV");
            payload.setCmdId("A015");
            payload.setIdDesc("AGV_ARRIVAL_EQP");
            payload.setTid(tid);

            A015CommandPayload.Message msg = new A015CommandPayload.Message();
            msg.setTid(tid);
            msg.setDeviceName(deviceName);
            msg.setDestLoc(destLoc);
            payload.setMessage(msg);

            String json = objectMapper.writeValueAsString(payload);
            return dispatch(targetSystem, "A015", json, tid, true);
        } catch (Exception e) {
            log.error("[MQTT] A015 指令發送失敗：{}", e.getMessage(), e);
            return MqttSendResult.fail("A015 指令發送失敗：" + e.getMessage(), tid);
        }
    }

    /* ===================================================================== */
    /* 通用固定/自動 TID 發布（保留你原本的方法）                              */
    /* ===================================================================== */

    /**
     * 以「固定 TID」發布任意指令（物件 payload 版）
     * - 不產生新 TID，沿用呼叫端提供的 tid
     * - requireAck: 是否需要等待 ACK（交給 Outbox/Worker 收斂）
     */
    public MqttSendResult publishSameTid(String targetSystem,
                                         String cmdId,
                                         String tid,
                                         Object payloadObject,
                                         boolean requireAck) {
        try {
            if (tid == null || tid.isBlank()) {
                return MqttSendResult.fail("publishSameTid: TID 不可為空", null);
            }
            String json = objectMapper.writeValueAsString(payloadObject);
            return dispatch(targetSystem, cmdId, json, tid, requireAck);
        } catch (Exception e) {
            log.error("[MQTT] publishSameTid 發送失敗（object→json 序列化失敗）：cmdId={}, tid={}, err={}",
                    cmdId, tid, e.getMessage(), e);
            return MqttSendResult.fail("publishSameTid 發送失敗：" + e.getMessage(), tid);
        }
    }

    /**
     * 以「固定 TID」發布任意指令（JSON 字串版）
     * - 不產生新 TID，沿用呼叫端提供的 tid
     */
    public MqttSendResult publishSameTid(String targetSystem,
                                         String cmdId,
                                         String tid,
                                         String jsonPayload,
                                         boolean requireAck) {
        if (tid == null || tid.isBlank()) {
            return MqttSendResult.fail("publishSameTid: TID 不可為空", null);
        }
        try {
            return dispatch(targetSystem, cmdId, jsonPayload, tid, requireAck);
        } catch (Exception e) {
            log.error("[MQTT] publishSameTid 發送失敗（json 直送）：cmdId={}, tid={}, err={}",
                    cmdId, tid, e.getMessage(), e);
            return MqttSendResult.fail("publishSameTid 發送失敗：" + e.getMessage(), tid);
        }
    }

    /**
     * 以「固定 TID」發布任意指令（物件 payload 版，預設不等 ACK）
     */
    public MqttSendResult publishSameTid(String targetSystem,
                                         String cmdId,
                                         String tid,
                                         Object payloadObject) {
        return publishSameTid(targetSystem, cmdId, tid, payloadObject, /* requireAck */ false);
    }

    /* ===================================================================== */
    /* 內部小工具：自動推導程式名稱與版本                                    */
    /* ===================================================================== */

    /**
     * 盡量從 spring.application.name 取得，沒有就用 "SAA"
     */
    private String resolveProgramName() {
        String springName = System.getProperty("spring.application.name");
        return (springName != null && !springName.isBlank()) ? springName : "SAA";
    }

    /**
     * 盡量從 Package Implementation-Version 取得，沒有就用 "dev"
     */
    private String resolveVersion() {
        Package pkg = this.getClass().getPackage();
        String implVer = (pkg != null) ? pkg.getImplementationVersion() : null;
        return (implVer != null && !implVer.isBlank()) ? implVer : "dev";
    }

    /* ===================================================================== */
    /* 內部小工具：派發（Outbox or 直送）                                     */
    /* ===================================================================== */

    /**
     * 送出（或入箱）統一從此進入
     *
     * @param targetSystem seec / ase
     * @param cmdId        指令代碼（如 S001、R007）
     * @param json         序列化後 Payload
     * @param tid          本次訊息 TID
     * @param requireAck   是否等待 ACK（Outbox 會據此重送/結案）
     */
    private MqttSendResult dispatch(String targetSystem, String cmdId, String json, String tid, boolean requireAck) {
        if (useOutbox) {
            // 入箱 + 立即嘗試一次；後續由 Outbox/Worker 管重試與 ACK
            return outbox.enqueueAndTrySend(cmdId, targetSystem, json, tid, requireAck);
        }
        // 傳統直送：立刻走 MQTT。呼叫端需自行負責 ACK/重送（不建議）
        return messageSender.send(targetSystem, cmdId, json, MqttMessageType.COMMAND, tid);
    }
}
