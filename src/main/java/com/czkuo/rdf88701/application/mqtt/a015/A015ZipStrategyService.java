package com.czkuo.rdf88701.application.mqtt.a015;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.A015AckPayload;
import com.czkuo.rdf88701.common.dto.mqtt.command.A015CommandPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import com.czkuo.rdf88701.domain.repository.RobotR007TaskRepository;
import com.czkuo.rdf88701.infra.entity.RobotR007Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 策略#1：STK01 / STK02
 * - 新版流程（先查→決策）：
 *   1) 先用 StatusQuery(Type=4, Name=Port) 取得該 Port 狀態
 *   2) 若「無產品」→ 只記錄不回 ACK
 *   3) 若「有產品但未鎖」→ 送 PortLockUnlock(Cmd=1) 鎖定，成功後再查一次 Type=4
 *   4) 只要達成「有產品且已鎖」→ 回 A015 ACK(DONE)
 *
 * 重要說明：
 *   - Cmd=1 → 鎖定（AMR 準備取料）
 *   - Cmd=2 → 解鎖（AMR 手臂離開 Port 位）
 *   - StatusQuery Type=4 回覆（逐 Port）：
 *       Name    : Port 名（如 STK01）
 *       Status  : 51=鎖定（僅參考）
 *       Message : [0]=Carrier（非空表示有產品）
 *                 [1]=鎖定旗標（"1"=鎖定, "2"=解除）
 *
 * 注意：
 *   - 僅在狀態正確（有產品且鎖定）時回 DONE；否則僅記錄，不回 ACK（OK 由對方回）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A015ZipStrategyService {

    private final ZipStockerCommandService zip;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper mapper;
    private final RobotR007TaskRepository taskRepo;

    /** 預設送往 ZIPA；如環境有 ZIPB，請視 DEST_LOC 轉成相對應 ZipTarget */
    @Value("${app.a015.zip.target:ZIPA}")
    private String zipTargetName;

    /**
     * 處理 STK01/02 到站：先查 Type=4 → 視情況鎖定 → 達成條件才 DONE
     * @param fromSystem 原始來源系統（預期為 SEEC）
     * @param cmd        A015 原始 payload
     */
    public void handle(String fromSystem, A015CommandPayload cmd) {
        String tid  = cmd.getTid();
        String dest = (cmd.getMessage() != null) ? cmd.getMessage().getDestLoc() : "";

        int idx = dest.indexOf("_");
        if (idx > 0) {
            dest = dest.substring(0, idx);
        }

        try {
            ZipTarget target = ZipTarget.valueOf(zipTargetName.toUpperCase());

            // 1) 先查 Type=4 狀態
            CheckResult first = queryType4(target, dest);
            if (!first.found) {
                log.warn("[A015][ZIP] Type=4 未找到目標或回覆為空，tid={}, dest={}", tid, dest);
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return; // 僅記錄，不回 ACK
            }

            if (!first.hasCarrier) {
                log.info("[A015][ZIP] Port 無產品（carrier 空），不回 ACK，tid={}, dest={}", tid, dest);
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return; // 僅記錄，不回 ACK
            }

//            String taskTid = (cmd.getMessage() != null) ? cmd.getMessage().getTid() : "";
//            if (taskTid.isEmpty()) {
//                log.info("[A015][ZIP] 任務 TID 為空，tid={}, dest={}, taskTid={}", tid, dest, taskTid);
//                sendAck(fromSystem, tid, cmd, "FAIL", "");
//                return; // 僅記錄，不回 ACK
//            }
//
//            var tOpt = taskRepo.findByAmrTid(taskTid);
//            if (tOpt.isEmpty()) {
//                log.info("[A015][ZIP] 未找到相關任務，tid={}, dest={}, taskTid={}", tid, dest, taskTid);
//                sendAck(fromSystem, tid, cmd, "FAIL", "");
//                return; // 僅記錄，不回 ACK
//            }
//
//            RobotR007Task t = tOpt.get();
//            String carrierId = t.getCarrierId();
//            if (!carrierId.equals(first.carrierId)) {
//                log.info("[A015][ZIP] carrierId 不匹配，tid={}, dest={}, carrierId={}, portId={}", tid, dest, carrierId, first.carrierId);
//                sendAck(fromSystem, tid, cmd, "RETRY", "");
//                return; // 僅記錄，不回 ACK
//            }

            if (first.isLocked) {
                // 2-a) 已經「有產品且鎖定」→ 直接 DONE
                log.info("[A015][ZIP] 已有產品且已鎖定，直接 DONE，tid={}, dest={}", tid, dest);
                sendAck(fromSystem, tid, cmd, "DONE", "");
                return;
            }

            // 2-b) 有產品但未鎖 → 送鎖定（Cmd=1）
            log.info("[A015][ZIP] 有產品但未鎖，送 PortLockUnlock(Cmd=1)，target={}, port={}, tid={}",
                    target, dest, tid);
            Root<PortLockUnlockSecondaryBody> lockResp = zip.sendPortLockUnlock(target, dest, /*cmd*/1);

            boolean lockOk = lockResp != null
                    && lockResp.getBody() != null
                    && lockResp.getBody().getResultInfos() != null
                    && !lockResp.getBody().getResultInfos().isEmpty()
                    && (lockResp.getBody().getResultInfos().get(0).getResult() == 0
                    || lockResp.getBody().getResultInfos().get(0).getResult() == 102);
            if (!lockOk) {
                String code = (lockResp == null || lockResp.getBody() == null
                        || lockResp.getBody().getResultInfos() == null
                        || lockResp.getBody().getResultInfos().isEmpty())
                        ? "NO_RESULT"
                        : ("ZIP_RESULT=" + lockResp.getBody().getResultInfos().get(0).getResult());
                log.warn("[A015][ZIP] PortLockUnlock 失敗，tid={}, dest={}, {}", tid, dest, code);
                sendAck(fromSystem, tid, cmd, "RETRY", "");
                return; // 僅記錄，不回 ACK
            }

            // 3) 再查 Type=4，確認是否達成「有產品且已鎖定」
            CheckResult second = queryType4(target, dest);
            if (second.found && second.hasCarrier && second.isLocked) {
                log.info("[A015][ZIP] 鎖定後已達成條件（有產品且鎖定），DONE，tid={}, dest={}", tid, dest);
                sendAck(fromSystem, tid, cmd, "DONE", "");
                return;
            }

            log.warn("[A015][ZIP] 鎖定後仍未達成條件（hasCarrier={}, isLocked={}），tid={}, dest={}",
                    second.hasCarrier, second.isLocked, tid, dest);
            sendAck(fromSystem, tid, cmd, "RETRY", "");
            // 僅記錄，不回 ACK

        } catch (Exception e) {
            log.error("[A015][ZIP] 例外，tid={}, dest={}, err={}", tid, dest, e.getMessage(), e);
            // 僅記錄，不回 ACK
        }
    }

    /**
     * 查詢 ZIP StatusQuery(Type=4, Name=destLoc)：
     *   - hasCarrier：Message[0] 非空
     *   - isLocked  ：Message[1] == "1"（"2" 代表解除）
     * 正確用法：使用 ZipStockerCommandService#queryPorts(ZipTarget, String...)，而非 queryAllSlots。
     */
    private CheckResult queryType4(ZipTarget target, String destLoc) {
        try {
            Root<StatusQuerySecondaryBody> resp = zip.queryPorts(target, destLoc);
            if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) {
                log.warn("[A015][ZIP] StatusQuery(Type=4) 為空：target={}, destLoc={}", target, destLoc);
                return CheckResult.notFound();
            }

            for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
                if (s == null) continue;
                // 只看 Type=4 & Name 相符（queryPorts 已經只查這個 Name，但保守再過濾一次）
                if (s.getType() != 4) continue;
                String name = safe(s.getName());
                if (!name.equalsIgnoreCase(safe(destLoc))) continue;

                int status = s.getStatus();
                List<?> msg = s.getMessage();

                // carrier：Message[0]
                String carrier = (msg != null && !msg.isEmpty() && msg.get(0) != null)
                        ? safe(msg.get(0).toString())
                        : "";
                boolean hasCarrier = !carrier.isEmpty();

                // lockFlag："1"=鎖定, "2"=解除
                String lockFlag = (msg != null && msg.size() >= 2 && msg.get(1) != null)
                        ? safe(msg.get(1).toString())
                        : "";
                boolean isLocked = "1".equals(lockFlag);

                log.info("[A015][ZIP] Type=4 檢查：dest={}, status={}, msg0(carrier)='{}', msg1(lockFlag)='{}' → hasCarrier={}, isLocked={}",
                        destLoc, status, carrier, lockFlag, hasCarrier, isLocked);

                return CheckResult.found(hasCarrier, isLocked, carrier);
            }

            log.warn("[A015][ZIP] Type=4 未找到目標：destLoc={}", destLoc);
            return CheckResult.notFound();

        } catch (Exception ex) {
            log.error("[A015][ZIP] 查 Type=4 失敗：destLoc={}, err={}", destLoc, ex.getMessage(), ex);
            return CheckResult.notFound();
        }
    }

    private static String safe(Object o) {
        if (o == null) return "";
        String s = o.toString().trim();
        return (s == null) ? "" : s;
    }

    /** 依你慣用方式：先寫 log，再用 publisher 直送 ACK */
    private void sendAck(String targetSystem, String tid, A015CommandPayload cmd, String result, String resultMessage) {
        try {
            A015AckPayload ack = new A015AckPayload();
            ack.setCmd("AGV");
            ack.setCmdId("A015");
            ack.setTid(tid);
            ack.setResult(result);
            ack.setResultMessage(resultMessage);

            A015AckPayload.Message msg = new A015AckPayload.Message();
            if (cmd.getMessage() != null) {
                msg.setTid(cmd.getMessage().getTid());
                msg.setDeviceName(cmd.getMessage().getDeviceName());
                msg.setDestLoc(cmd.getMessage().getDestLoc());
            }
            ack.setMessage(msg);

            logService.recordReturningId(
                    "ack/a015",
                    "a015-zip-strategy",
                    targetSystem,
                    mapper.valueToTree(ack),
                    MqttMessageType.ACK
            );
            publisher.publish(targetSystem,
                    mapper.writeValueAsString(ack),
                    MqttMessageType.ACK,
                    ack.getTid(),
                    ack.getCmdId());
        } catch (Exception e) {
            log.error("[A015][ZIP] 發送 ACK 失敗，tid={}, err={}", tid, e.getMessage(), e);
        }
    }

    /** 查詢結果小結構 */
    private static final class CheckResult {
        final boolean found;
        final boolean hasCarrier;
        final boolean isLocked;
        final String carrierId;

        private CheckResult(boolean found, boolean hasCarrier, boolean isLocked, String carrierId) {
            this.found = found;
            this.hasCarrier = hasCarrier;
            this.isLocked = isLocked;
            this.carrierId = carrierId;
        }
        static CheckResult notFound() { return new CheckResult(false, false, false, ""); }
        static CheckResult found(boolean hasCarrier, boolean isLocked, String carrierId) {
            return new CheckResult(true, hasCarrier, isLocked, carrierId);
        }
    }
}
