package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.MqttInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 入站 COMMAND 處理佇列表（獨立於 mqtt_message_log） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-27
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface MqttInboxMapper extends BaseMapper<MqttInbox> {

    /** INSERT IGNORE 入隊（依 log_id 唯一鍵防重） */
    int insertIgnore(MqttInbox row);

    /** 依 log_id 取回 id */
    Long selectIdByLogId(@Param("logId") Long logId);

    /** 選一筆候選 id（FOR UPDATE SKIP LOCKED） */
    Long selectCandidateIdForUpdate();

    /**
     * 只挑指定指令別的候選 id（FOR UPDATE SKIP LOCKED）
     * 例：只處理 R029 → cmdId="R029"
     */
    Long selectCandidateIdForUpdateByCmd(@Param("cmdId") String cmdId);
    Long selectCandidateIdForUpdateByCmdNoNextAttemptTime(@Param("cmdId") String cmdId);

    /** 將 id 佔鎖為 IN_PROGRESS（含 TTL、attempts+1；條件保護） */
    int updateToInProgress(@Param("id") Long id,
                           @Param("workerId") String workerId,
                           @Param("ttlSec") int ttlSec);

    int updateToInProgressNoNextAttemptTime(@Param("id") Long id,
                                            @Param("workerId") String workerId,
                                            @Param("ttlSec") int ttlSec);

    /** 調整優先權（1 高 → 9 低） */
    int updatePriority(@Param("id") Long id, @Param("priority") int priority);

    /** 標記為 QUEUED（釋放鎖） */
    int markQueued(@Param("id") Long id);

    /** 標記為 DONE 並回填對應任務資訊 */
    int markDone(@Param("id") Long id,
                 @Param("mappedTaskType") String mappedTaskType,
                 @Param("mappedTaskId") Long mappedTaskId);

    /** 標記為 REJECTED（附加原因到 process_errors） */
    int markRejected(@Param("id") Long id, @Param("reason") String reason);

    /** 標記為 CANCELLED（附加原因到 process_errors） */
    int markCancelled(@Param("id") Long id, @Param("reason") String reason);

    /**
     * 釋放逾時鎖：
     * process_state='IN_PROGRESS' 且 lock_until < NOW() → 轉回 QUEUED 並立即可再撿
     */
    int releaseExpiredLocks();

    /** 退避重排：回 QUEUED 並設定 next_attempt_time = NOW() + seconds */
    int requeueWithBackoff(@Param("id") Long id, @Param("seconds") int seconds);
}
