package com.czkuo.rdf88701.application.service.process;

import com.czkuo.rdf88701.application.service.zip.ZipStockerCommandService;
import com.czkuo.rdf88701.common.dto.DeviceProcessState;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.common.enums.ZipTarget;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQueryPrimaryBody;
import com.czkuo.rdf88701.domain.dto.zip.StatusQuery.StatusQuerySecondaryBody;
import com.czkuo.rdf88701.domain.dto.zip.common.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZipProcessStateProvider {

    private final ZipStockerCommandService zip;

    public DeviceProcessState queryZipa() {
        return queryInternal(ZipTarget.ZIPA, "ZIPA");
    }

    public DeviceProcessState queryZipb() {
        return queryInternal(ZipTarget.ZIPB, "ZIPB");
    }

    private DeviceProcessState queryInternal(ZipTarget target, String logicalName) {
        try {
            // 準備多筆 QueryInfos: Type=0, Type=1
            var qi0 = new StatusQueryPrimaryBody.QueryInfo();
            qi0.setType(0); qi0.setName("");

            var qi1 = new StatusQueryPrimaryBody.QueryInfo();
            qi1.setType(1); qi1.setName("");

            Root<StatusQuerySecondaryBody> resp =
                    zip.sendStatusQuery(target, List.of(qi0, qi1));

            // 解析 Type=0 倉儲旗標
            int flagStatus = resp.getBody().getStatusInfos().stream()
                    .filter(x -> Integer.valueOf(0).equals(x.getType()))
                    .map(x -> Optional.ofNullable(x.getStatus()).orElse(0))
                    .findFirst().orElse(0);

            // 解析 Type=1 手臂狀態
            int armStatus = resp.getBody().getStatusInfos().stream()
                    .filter(x -> Integer.valueOf(1).equals(x.getType()))
                    .map(x -> Optional.ofNullable(x.getStatus()).orElse(0))
                    .findFirst().orElse(0);

            // 判斷最終狀態
            ProcessStatus st;
            String msg = "ZIP flag=" + flagStatus + ", arm=" + armStatus;

//            if ((flagStatus & 8) != 0) {
//                st = ProcessStatus.ERROR;
//            } else if ((flagStatus & 4) != 0) {
//                st = ProcessStatus.WARNING;
//            } else if (armStatus == 21) {
//                st = ProcessStatus.RUN;
//            } else if (armStatus == 23) {
//                st = ProcessStatus.IDLE;
//            } else if (armStatus == 22) {
//                st = ProcessStatus.STOP;
//            } else if ((flagStatus & 1) != 0) {
//                st = ProcessStatus.RUN;   // Auto 但沒回 arm → RUN
//            } else if ((flagStatus & 2) != 0) {
//                st = ProcessStatus.IDLE;  // Manual → IDLE
//            } else {
//                st = ProcessStatus.STOP;
//            }

            if (armStatus == 21) {
                st = ProcessStatus.RUN;
            } else if (armStatus == 23) {
                st = ProcessStatus.IDLE;
            } else if (armStatus == 22) {
                st = ProcessStatus.STOP;
            } else {
                st = ProcessStatus.STOP;
            }

            return new DeviceProcessState(logicalName, st, msg);
        } catch (Exception ex) {
            log.warn("[{}] ZIP query failed: {}", logicalName, ex.getMessage());
            return new DeviceProcessState(logicalName, ProcessStatus.WARNING, "ZIP:query-failed");
        }
    }
}
