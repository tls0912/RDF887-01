package com.czkuo.rdf88701.application.monitor.History;


import com.czkuo.rdf88701.application.service.History.*;
import com.czkuo.rdf88701.domain.event.HistoryEvent;
import com.czkuo.rdf88701.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryFlushMonitor {

    private static final int BATCH_SIZE = 50;

    private final CraneRequestHistoryInsertService craneRequestHistoryInsertService;
    private final GripperRequestHistoryInsertService gripperRequestHistoryInsertService;
    private final InfraredRequestHistoryInsertService infraredRequestHistoryInsertService;
    private final TransferRequestHistoryInsertService transferRequestHistoryInsertService;
    private final WorkingBeamRequestHistoryInsertService workingBeamRequestHistoryInsertService;
    private final CraneTaskHistoryInsertService craneTaskHistoryInsertService;
    private final GripperTaskHistoryInsertService gripperTaskHistoryInsertService;
    private final InfraredTaskHistoryInsertService infraredTaskHistoryInsertService;
    private final TransferTaskHistoryInsertService transferTaskHistoryInsertService;
    private final WorkingBeamTaskHistoryInsertService workingBeamTaskHistoryInsertService;
    private final ContainerMainHistoryInsertService containerMainHistoryInsertService;
    private final ContainerDataHistoryInsertService containerDataHistoryInsertService;

    private final CraneRequestHistoryMapper craneRequestHistoryMapper;
    private final GripperRequestHistoryMapper gripperRequestHistoryMapper;
    private final InfraredRequestHistoryMapper infraredRequestHistoryMapper;
    private final TransferRequestHistoryMapper transferRequestHistoryMapper;
    private final WorkingBeamRequestHistoryMapper workingBeamRequestHistoryMapper;
    private final CraneTaskHistoryMapper craneTaskHistoryMapper;
    private final GripperTaskHistoryMapper gripperTaskHistoryMapper;
    private final InfraredTaskHistoryMapper infraredTaskHistoryMapper;
    private final TransferTaskHistoryMapper transferTaskHistoryMapper;
    private final WorkingBeamTaskHistoryMapper workingBeamTaskHistoryMapper;
    private final ContainerMainHistoryMapper containerMainHistoryMapper;
    private final ContainerDataHistoryMapper containerDataHistoryMapper;

    @Scheduled(fixedDelay = 20_000, initialDelay = 5_000)
    public void flush() {
        flushHistory("CraneRequestHistory", craneRequestHistoryInsertService.poll(BATCH_SIZE),
                craneRequestHistoryInsertService::toHistory, craneRequestHistoryMapper::insert);

        flushHistoryBatch("GripperRequestHistory", gripperRequestHistoryInsertService.poll(BATCH_SIZE),
                gripperRequestHistoryInsertService::toHistory, gripperRequestHistoryMapper::batchInsert);

        flushHistory("InfraredRequestHistory", infraredRequestHistoryInsertService.poll(BATCH_SIZE),
                infraredRequestHistoryInsertService::toHistory, infraredRequestHistoryMapper::insert);

        flushHistory("GripperRequestHistory", transferRequestHistoryInsertService.poll(BATCH_SIZE),
                transferRequestHistoryInsertService::toHistory, transferRequestHistoryMapper::insert);

        flushHistory("WorkingBeamRequestHistory", workingBeamRequestHistoryInsertService.poll(BATCH_SIZE),
                workingBeamRequestHistoryInsertService::toHistory, workingBeamRequestHistoryMapper::insert);

        flushHistory("CraneTaskHistory", craneTaskHistoryInsertService.poll(BATCH_SIZE),
                craneTaskHistoryInsertService::toHistory, craneTaskHistoryMapper::insert);
        flushHistoryBatch("GripperTaskHistory", gripperTaskHistoryInsertService.poll(BATCH_SIZE),
                gripperTaskHistoryInsertService::toHistory, gripperTaskHistoryMapper::batchInsert);
        flushHistory("InfraredTaskHistory", infraredTaskHistoryInsertService.poll(BATCH_SIZE),
                infraredTaskHistoryInsertService::toHistory, infraredTaskHistoryMapper::insert);
        flushHistory("TransferTaskHistory", transferTaskHistoryInsertService.poll(BATCH_SIZE),
                transferTaskHistoryInsertService::toHistory, transferTaskHistoryMapper::insert);
        flushHistory("WorkingBeamTaskHistory", workingBeamTaskHistoryInsertService.poll(BATCH_SIZE),
                workingBeamTaskHistoryInsertService::toHistory, workingBeamTaskHistoryMapper::insert);

        flushHistory("ContainerMainHistory", containerMainHistoryInsertService.poll(BATCH_SIZE),
                containerMainHistoryInsertService::toHistory, containerMainHistoryMapper::insert);
        flushHistoryBatch("ContainerDataHistory", containerDataHistoryInsertService.poll(BATCH_SIZE),
                containerDataHistoryInsertService::toHistory, containerDataHistoryMapper::batchInsert);

    }

    private <T, H> void flushHistory(String name, List<HistoryEvent<T>> events,
                                     Function<HistoryEvent<T>, H> converter, Consumer<H> inserter) {
        try {
            if (events.isEmpty()) {
                return;
            }

            int size = events.size();
            log.info("[Flush] {} Start count={}", name, size);

            for (HistoryEvent<T> event : events) {
                H history = converter.apply(event);
                inserter.accept(history);
            }

            log.info("[Flush] {} success count={}", name, size);

        } catch (Exception ex) {
            log.error("[Flush] {} failed", name, ex);
        }
    }

    private <T, H> void flushHistoryBatch(String name, List<HistoryEvent<T>> events,
                                          Function<HistoryEvent<T>, H> converter,
                                          Function<List<H>, Integer> batchInserter) {
        try {
            if (events.isEmpty()) {
                return;
            }

            List<H> histories = new ArrayList<>(events.size());
            for (HistoryEvent<T> event : events) {
                histories.add(converter.apply(event));
            }

            log.info("[Flush] {} Start count={}", name, histories.size());
            batchInserter.apply(histories);
            log.info("[Flush] {} success count={}", name, histories.size());

        } catch (Exception ex) {
            log.error("[Flush] {} failed", name, ex);
        }
    }

}
