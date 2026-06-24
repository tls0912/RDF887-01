package com.czkuo.rdf88701.application.service.r029;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R029AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.R029OutputItem;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class R029OutputCaptureService {

    private final ContainerMainRepository containerMainRepository;
    private final RobotInR029LotRepository r029LotRepository;
    private final RobotR029TaskRepository r029TaskRepository;
    private final R029OutputItemRepository r029OutputItemRepository;
    private final LocationTrackingRepository locationTrackingRepository; // 用來抓現場所有 alias
    private final MqttMessageEventPublisher publisher;                   // 送 R029 END
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.target.r029:ase}")
    private String r029TargetSystem; // 送 R029 END 的目標系統（預設 ase）

    /** 打帶成功：寫一筆 state=STRAPPED（若已存在同 taskId+newCarrierId 則跳過） */
    public void recordStrappingIfBelongs(Long containerId) {
        upsertByStage(containerId, "STRAPPED");
    }

    /** 貼標成功：將 state 進位到 LABELED；若不存在則補一筆 state=LABELED */
    public void markLabeledIfBelongs(Long containerId) {
        upsertByStage(containerId, "LABELED");
    }

    /** 入料詢問（上架前詢問）：進位為 INQUIRY；若不存在則補一筆 INQUIRY */
    public void markInquiryIfBelongs(Long containerId) {
        upsertByStage(containerId, "INQUIRY");
    }

    /** 上架完成：進位為 SHELVED；若不存在則補一筆 SHELVED */
    public void markShelvedIfBelongs(Long containerId) {
        upsertByStage(containerId, "SHELVED");
    }

    /** 下架/移除：進位為 REMOVED；若不存在則補一筆 REMOVED */
    public void markRemovedIfBelongs(Long containerId) {
        upsertByStage(containerId, "REMOVED");
    }

    /** 出庫完成：進位為 STOCKED_OUT；若不存在則補一筆 STOCKED_OUT */
    public void markStockOutIfBelongs(Long containerId) {
        upsertByStage(containerId, "STOCKED_OUT");
    }

    /** 入料詢問（只有 CarrierID 可用）：進位為 INQUIRY；若不存在則補一筆 INQUIRY */
    public void markInquiryByCarrierId(String carrierId) {
        upsertByStageWithCarrierId(carrierId, "INQUIRY");
    }

    /** 上架完成（只有 CarrierID 可用）：進位為 SHELVED；若不存在則補一筆 SHELVED */
    public void markShelvedByCarrierId(String carrierId) { upsertByStageWithCarrierId(carrierId, "SHELVED"); }

    /** 下架/移除（CarrierID）：進位為 REMOVED；若不存在則補一筆 REMOVED */
    public void markRemovedByCarrierId(String carrierId) {
        upsertByStageWithCarrierId(carrierId, "REMOVED");
    }

    /** 出庫完成（CarrierID）：進位為 STOCKED_OUT；若不存在則補一筆 STOCKED_OUT */
    public void markStockOutByCarrierId(String carrierId) {
        upsertByStageWithCarrierId(carrierId, "STOCKED_OUT");
    }

    // --------------------------------------------------------------------
    // 共同邏輯：解析 active R029、匹配 base、依 stage 進行 upsert → 之後檢查是否可終結
    // --------------------------------------------------------------------

    /** 以 containerId 為主的 upsert */
    private void upsertByStage(Long containerId, String stage) {
        if (containerId == null) return;

        // 1) 取新載具序號
        Optional<ContainerMain> cOpt = containerMainRepository.findById(containerId);
        if (cOpt.isEmpty()) {
            log.warn("[R029] containerId={} 找不到 ContainerMain，略過", containerId);
            return;
        }
        String newCarrierId = StringUtils.trimToEmpty(cOpt.get().getAliasCode());
        if (newCarrierId.isEmpty()) {
            log.warn("[R029] containerId={} 的 alias_code 為空，略過", containerId);
            return;
        }

        // 2) 針對 newCarrierId 解析所屬的 R029 任務（支援同時多筆 open）
        Optional<TaskMatch> matchOpt = resolveTaskForNewCarrier(newCarrierId);
        if (matchOpt.isEmpty()) {
            //log.debug("[R029] newCarrierId={} 無法對應任何 open R029 任務，略過", newCarrierId);
            return;
        }
        TaskMatch match = matchOpt.get();
        RobotR029Task task = match.task();
        List<String> matchedBases = match.matchedBases();
        String canonicalFrom = R029BaseIdParser.canonicalizeMultipleBases(matchedBases);

        // 3) 依 stage upsert
        boolean changed = false; // 用來決定是否要檢查終結
        Optional<R029OutputItem> existsOpt =
                r029OutputItemRepository.findOneByTaskIdAndNewCarrierId(task.getId(), newCarrierId);

        if (existsOpt.isPresent()) {
            // 已存在 → 視情況進位狀態
            R029OutputItem it = existsOpt.get();
            String curr = Objects.toString(it.getState(), "");
            if (!stage.equalsIgnoreCase(curr)) {
                it.setState(stage);
                if (StringUtils.isBlank(it.getFromCarrierId())) {
                    it.setFromCarrierId(canonicalFrom);
                }
                it.setUpdatedTime(LocalDateTime.now());
                changed = r029OutputItemRepository.update(it);
                log.info("[R029] 更新輸出項目狀態 taskId={}, newCarrierId={}, {} -> {} ({})",
                        task.getId(), newCarrierId, curr, stage, changed ? "OK" : "FAIL");
            } else {
                //log.debug("[R029] 狀態未變更 taskId={}, newCarrierId={}, state={}", task.getId(), newCarrierId, curr);
            }
        } else {
            // 不存在 → 補一筆（可能之前漏了 STRAPPED），以目前 stage 落庫
            R029OutputItem it = new R029OutputItem();
            it.setTaskId(task.getId());
            it.setFromCarrierId(canonicalFrom);
            it.setNewCarrierId(newCarrierId);
            it.setPieces(task.getPiecePerLot());
            it.setState(stage);
            it.setCreatedTime(LocalDateTime.now());
            it.setUpdatedTime(LocalDateTime.now());
            changed = r029OutputItemRepository.save(it);
            log.info("[R029] 新增輸出項目 taskId={}, newCarrierId={}, state={} -> {}",
                    task.getId(), newCarrierId, stage, changed ? "OK" : "FAIL");
        }

        // 狀態有變更才檢查是否可終結
        if (changed) {
            checkAndFinalizeIfComplete(task);
        }
    }

    /** 以 carrierId（alias_code）為主的 upsert（會查出 containerId 再重用上面的流程） */
    private void upsertByStageWithCarrierId(String carrierId, String stage) {
        String cid = StringUtils.trimToEmpty(carrierId);
        if (cid.isEmpty()) return;

        try {
            Optional<ContainerMain> cmOpt = containerMainRepository.findByAliasCode(cid);
            if (cmOpt.isEmpty()) {
                //log.debug("[R029] carrierId(alias_code)={} 找不到 ContainerMain，略過 stage={}", cid, stage);
                return;
            }
            upsertByStage(cmOpt.get().getId(), stage);
        } catch (Throwable t) {
            log.warn("[R029] upsertByStageWithCarrierId error, carrierId={}, stage={}, err={}",
                    cid, stage, t.getMessage(), t);
        }
    }

    // --------------------------------------------------------------------
    // 任務歸屬解析
    // --------------------------------------------------------------------

    /**
     * 針對 newCarrierId 解析所屬任務與命中 base 清單：
     * 1) 取所有 open 任務
     * 2) 對每任務用 logId 查 base list
     * 3) 用 R029BaseIdParser.extractCandidateBases(newCarrierId) 產生候選 base
     * 4) 交集非空者列為候選
     * 5) 多筆同時命中 → PROCESSING 優先；其次 createdTime 新者；最後 id 較大者
     */
    private Optional<TaskMatch> resolveTaskForNewCarrier(String newCarrierId) {
        if (StringUtils.isBlank(newCarrierId)) return Optional.empty();

        // 取 open 任務
        List<RobotR029Task> open = r029TaskRepository.findOpen();
        if (open == null || open.isEmpty()) return Optional.empty();

        // 候選 base（從 newCarrierId 解析）
        List<String> candidateBases = R029BaseIdParser.extractCandidateBases(newCarrierId);
        if (candidateBases == null || candidateBases.isEmpty()) return Optional.empty();

        Set<String> candLower = candidateBases.stream()
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // 逐任務比對 base，蒐集命中者
        List<TaskMatch> candidates = new ArrayList<>();
        for (RobotR029Task t : open) {
            Long logId = t.getLogId();
            if (logId == null) continue;

            List<String> taskBases = r029LotRepository.findCarrierIdsByLogId(logId);
            if (taskBases == null || taskBases.isEmpty()) continue;

            // lower→orig 對照，交集命中者回復原大小寫
            Map<String, String> lower2orig = taskBases.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            s -> s.toLowerCase(Locale.ROOT),
                            s -> s,
                            (a, b) -> a
                    ));

            List<String> matched = lower2orig.entrySet().stream()
                    .filter(e -> candLower.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();

            if (!matched.isEmpty()) {
                candidates.add(new TaskMatch(t, matched));
            }
        }

        if (candidates.isEmpty()) return Optional.empty();

        // 多筆命中 → 排序規則：PROCESSING 優先，其次 createdTime（新）或 id（大）
        candidates.sort((a, b) -> {
            int ap = isProcessing(a.task()) ? 0 : 1;
            int bp = isProcessing(b.task()) ? 0 : 1;
            if (ap != bp) return Integer.compare(ap, bp);
            // 次序：較新者優先
            LocalDateTime at = safeCreatedTime(a.task());
            LocalDateTime bt = safeCreatedTime(b.task());
            int timeCmp = bt.compareTo(at); // desc
            if (timeCmp != 0) return timeCmp;
            // 後備以 id 大者優先
            long aid = a.task().getId() == null ? 0L : a.task().getId();
            long bid = b.task().getId() == null ? 0L : b.task().getId();
            return Long.compare(bid, aid);
        });

        return Optional.of(candidates.get(0));
    }

    private boolean isProcessing(RobotR029Task t) {
        return t != null && "PROCESSING".equalsIgnoreCase(StringUtils.defaultString(t.getInternalState(), ""));
    }

    private LocalDateTime safeCreatedTime(RobotR029Task t) {
        try {
            // 若你的 entity 有 getCreatedTime() 就用它；沒有就回 MIN 當最舊
            var m = RobotR029Task.class.getMethod("getCreatedTime");
            Object v = m.invoke(t);
            if (v instanceof LocalDateTime ldt) return ldt;
        } catch (Throwable ignore) { }
        return LocalDateTime.MIN; // 當作最舊
    }

    /** 小型封裝：命中的任務與它命中的 base 清單 */
    private record TaskMatch(RobotR029Task task, List<String> matchedBases) {}

    // --------------------------------------------------------------------
    // 終結檢查與送 END
    // --------------------------------------------------------------------
    private void checkAndFinalizeIfComplete(RobotR029Task task) {
        try {
            Long logId = task.getLogId();
            if (logId == null) return;

            // a) 取任務 base 清單
            List<String> taskBases = r029LotRepository.findCarrierIdsByLogId(logId);
            if (taskBases == null || taskBases.isEmpty()) return;

            // 標準化（lower）
            Set<String> baseLower = taskBases.stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            // b) LocationTracking：抓現場所有 alias，若任何一個現場 alias 的 candidateBases 與 base 有交集 → 尚未完成
            List<String> presentAliass;
            try {
                presentAliass = locationTrackingRepository.findPresentAliasCodesNot272829();
            } catch (Throwable t) {
                log.warn("[R029] 無法取得現場 alias 清單，略過終結檢查（{}）", t.getMessage());
                return;
            }

            boolean anyPresentRelated = false;
            if (presentAliass != null && !presentAliass.isEmpty()) {
                for (String s : presentAliass) {
                    String alias = StringUtils.trimToEmpty(s);
                    if (alias.isEmpty()) continue;
                    List<String> cand = R029BaseIdParser.extractCandidateBases(alias);
                    for (String c : cand) {
                        if (baseLower.contains(c.toLowerCase(Locale.ROOT))) {
                            anyPresentRelated = true;
                            break;
                        }
                    }
                    if (anyPresentRelated) break;
                }
            }
            if (anyPresentRelated) {
                //log.debug("[R029] 還有相同/衍生 alias 在場，taskId={} 不可終結", task.getId());
                return;
            }

            // c) R029OutputItem：不得存在 pending 狀態（STRAPPED / LABELED / INQUIRY）
            List<R029OutputItem> items;
            try {
                items = r029OutputItemRepository.findByTaskId(task.getId());
            } catch (Throwable t) {
                log.warn("[R029] 取任務輸出項目失敗，略過終結檢查（{}）", t.getMessage());
                return;
            }

//            if (items != null && !items.isEmpty()) {
//                //final Set<String> pending = Set.of("STRAPPED", "LABELED", "INQUIRY");
//                final Set<String> pending = Set.of("STRAPPED");
//                boolean hasPending = items.stream()
//                        .map(it -> StringUtils.defaultString(it.getState(), ""))
//                        .anyMatch(st -> pending.contains(st.toUpperCase(Locale.ROOT)));
//                if (hasPending) {
//                    //log.debug("[R029] 任務內仍有待處理狀態（STRAPPED/LABELED/INQUIRY），taskId={} 不可終結", task.getId());
//                    //log.debug("[R029] 任務內仍有待處理狀態（STRAPPED），taskId={} 不可終結", task.getId());
//                    return;
//                }
//            }
            // 若 items 為空也視為可終結（代表沒有未完成的項目留存）

            // d) 嘗試把任務從 PROCESSING → COMPLETED（會自動把 external_last_result 設為 END）
            boolean updated = r029TaskRepository.updateStateByLogId(task.getLogId(), "PROCESSING", "COMPLETED", null);
            if (!updated) {
                // 可能已被他處終結；不重覆送 MQTT
                //log.debug("[R029] 任務狀態未更新（可能已完成或非 PROCESSING），taskId={}", task.getId());
                return;
            }

            // e) 送 R029 END（以同 TID 回 ACK）
            try {
                // 1) 準備 CarrierID List（優先取「完成狀態」輸出項目的 new_carrier_id）
                // final Set<String> pending = Set.of("STRAPPED", "LABELED", "INQUIRY");
                // List<R029OutputItem> allItems = r029OutputItemRepository.findByTaskId(task.getId());

                // List<String> completedNewIds = (allItems == null ? List.<R029OutputItem>of() : allItems).stream()
                //         .filter(Objects::nonNull)
                //         .filter(it -> StringUtils.isNotBlank(it.getNewCarrierId()))
                //         .filter(it -> {
                //             String st = StringUtils.defaultString(it.getState(), "");
                //             return !pending.contains(st.toUpperCase(Locale.ROOT)); // 完成 = 不在 pending 之列
                //         })
                //         .map(R029OutputItem::getNewCarrierId)
                //         .distinct()
                //         .toList();

                // 若沒有可用的完成清單，fallback 到 base 清單，避免對端拿到空陣列
                // List<String> carrierIdsForEnd;
                // if (completedNewIds != null && !completedNewIds.isEmpty()) {
                //     carrierIdsForEnd = completedNewIds;
                // } else {
                //     List<String> baseIds = r029LotRepository.findCarrierIdsByLogId(task.getLogId());
                //     carrierIdsForEnd = (baseIds == null ? List.<String>of() :
                //             baseIds.stream().filter(StringUtils::isNotBlank).distinct().toList());
                // }

                // 1) 準備 CarrierID List
                List<String> baseIds = r029LotRepository.findCarrierIdsByLogId(task.getLogId());
                List<String>carrierIdsForEnd = (baseIds == null ? List.of() :
                        baseIds.stream().filter(StringUtils::isNotBlank).distinct().toList());

                // 2) 組 ACK Payload（依你的 DTO 欄位）
                R029AckPayload ack = new R029AckPayload();
                ack.setCmd("ROBOT");
                ack.setCmdId("R029");
                ack.setTid(task.getTid());
                ack.setIdDesc("MOVE_LOTS_TO_DISMANTLE_AND_TIE");

                R029AckPayload.Message ackMsg = new R029AckPayload.Message();
                ackMsg.setCarrierList(toCarrierInfoList(carrierIdsForEnd));
                ackMsg.setCount(String.valueOf(task.getPiecePerLot()));
                ackMsg.setTrayType(task.getTrayType());
                ackMsg.setTrayDesc(task.getTrayDesc());

                ack.setMessage(ackMsg);
                ack.setResult("END");
                ack.setResultMessage("completed");

                // 3) 發送（若你已實作此 API，直接用；否則改用你現有的 publish 流程）
                logService.recordReturningId("ack/r029", "saa", r029TargetSystem, objectMapper.valueToTree(ack), MqttMessageType.ACK);
                publisher.publish(r029TargetSystem, objectMapper.writeValueAsString(ack), MqttMessageType.ACK, task.getTid(), "R029");

                log.info("[R029] 任務終結完成並已送 R029 END：taskId={}, tid={}, carriers={}",
                        task.getId(), task.getTid(), carrierIdsForEnd);

            } catch (Exception ex) {
                log.warn("[R029] 已終結但送 R029 END 失敗：taskId={}, tid={}, err={}",
                        task.getId(), task.getTid(), ex.getMessage(), ex);
            }

        } catch (Exception e) {
            log.warn("[R029] checkAndFinalizeIfComplete 發生例外：taskId={}, err={}", task.getId(), e.getMessage(), e);
        }
    }

    private static List<R029AckPayload.CarrierInfo> toCarrierInfoList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<R029AckPayload.CarrierInfo> list = new ArrayList<>();
        for (String id : ids) {
            if (StringUtils.isBlank(id)) continue;
            R029AckPayload.CarrierInfo ci = new R029AckPayload.CarrierInfo();
            ci.setCarrierId(id);
            list.add(ci);
        }
        return list;
    }
}
