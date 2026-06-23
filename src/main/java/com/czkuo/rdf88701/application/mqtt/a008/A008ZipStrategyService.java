package com.czkuo.rdf88701.application.mqtt.a008;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.mqtt.command.A008CommandPayload;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.PortLockUnlock.PortLockUnlockSecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class A008ZipStrategyService {

    private final ZipStockerCommandService zip;

    @Value("${app.a015.zip.target:ZIPA}")
    private String zipTargetName;

    private static final Set<String> PORTS = Set.of("STK01", "STK02");

    /**
     * 若符合條件（STK01/02 且 JOB_STATUS=OUTPUT_END/MOVING_START），執行 ZIP 解鎖，並回傳是否有執行。
     */
    public boolean handle(A008CommandPayload cmd) {
        var msg = cmd.getMessage();
        String dest = up(msg != null ? msg.getDestLoc() : null);
        String job  = up(msg != null ? msg.getJobStatus() : null);

        int idx = dest.indexOf("_");
        if (idx > 0) {
            dest = dest.substring(0, idx);
        }

        if (!PORTS.contains(dest)) return false;
        if (!"OUTPUT_END".equals(job)) return false;
        // if (!("OUTPUT_END".equals(job) || "MOVING_START".equals(job))) return false;

        ZipTarget target = ZipTarget.valueOf(zipTargetName.toUpperCase());
        String tid = cmd.getTid();
        log.info("[A008][ZIP-UNLOCK] PortLockUnlock cmd=2 → target={}, port={}, tid={}, job={}", target, dest, tid, job);

        try {
            Root<PortLockUnlockSecondaryBody> resp = zip.sendPortLockUnlock(target, dest, /*cmd*/2);
            boolean ok = resp != null
                    && resp.getBody() != null
                    && resp.getBody().getResultInfos() != null
                    && !resp.getBody().getResultInfos().isEmpty()
                    && (resp.getBody().getResultInfos().get(0).getResult() == 0);

            if (!ok) {
                String code = (resp == null || resp.getBody() == null || resp.getBody().getResultInfos() == null || resp.getBody().getResultInfos().isEmpty())
                        ? "NO_RESULT"
                        : ("ZIP_RESULT=" + resp.getBody().getResultInfos().get(0).getResult());
                log.warn("[A008][ZIP-UNLOCK] 解除失敗：port={}, tid={}, {}", dest, tid, code);
                return true; // 已嘗試執行
            }

            // 可選：Type=4 驗證 lockFlag 已變 "2"（解除）或 status != 51
            try {
                Root<StatusQuerySecondaryBody> check = zip.queryPorts(target, dest);
                boolean unlocked = verifyZipUnlocked(check, dest);
                log.info("[A008][ZIP-UNLOCK] 驗證結果：port={}, unlocked={}", dest, unlocked);
            } catch (Exception ex) {
                //log.debug("[A008][ZIP-UNLOCK] 驗證略過：{}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("[A008][ZIP-UNLOCK] 呼叫失敗：port={}, tid={}, err={}", dest, tid, ex.getMessage(), ex);
        }
        return true;
    }

    private boolean verifyZipUnlocked(Root<StatusQuerySecondaryBody> resp, String port) {
        if (resp == null || resp.getBody() == null || resp.getBody().getStatusInfos() == null) return false;
        for (StatusQuerySecondaryBody.StatusInfo s : resp.getBody().getStatusInfos()) {
            if (s == null || s.getType() != 4) continue;
            if (!port.equalsIgnoreCase(String.valueOf(s.getName()))) continue;

            List<?> msg = s.getMessage();
            String lockFlag = (msg != null && msg.size() >= 2 && msg.get(1) != null)
                    ? String.valueOf(msg.get(1)).trim()
                    : "";
            return "2".equals(lockFlag);
        }
        return false;
    }

    private static String up(String s) { return s == null ? null : s.trim().toUpperCase(); }
}
