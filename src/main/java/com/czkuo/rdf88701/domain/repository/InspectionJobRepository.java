package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.InspectionJob;
import java.util.List;
import java.util.Optional;

/**
 * 異物檢任務 Repository
 *
 * 設計重點：
 *  1) 每支夾爪同時僅允許一筆未關閉任務（靠 DB 的「產生欄位 + UNIQUE 索引」實現）。
 *  2) 大多數更新都以「is_closed = 0」為條件，避免關到歷史單（冪等、安全）。
 *  3) 提供快閃補關：相機快速回 IDLE、poll 漏到 SECOND_DONE 的情況下，依計數成長補「SECOND_DONE → DONE」。
 */
public interface InspectionJobRepository {

    // ===== 基本 CRUD =====

    Optional<InspectionJob> findById(Long id);

    boolean save(InspectionJob entity);

    boolean update(InspectionJob entity);

    boolean deleteById(Long id);

    List<InspectionJob> findAll();

    // ===== 進行中（未關閉）任務查詢 =====

    /** 取所有未關閉的任務（is_closed=0），依建立時間、id 由小到大排序（方便排程依序處理） */
    List<InspectionJob> findActiveJobs();

    /** 該夾爪是否已有進行中的異物檢任務（避免重複開單） */
    boolean existsActiveForGripper(Long gripperId);

    /** 取得該夾爪目前進行中的任務（is_closed=0） */
    Optional<InspectionJob> findActiveByGripper(Long gripperId);

    // ===== 狀態更新（皆僅對 is_closed=0 的任務生效） =====

    /**
     * 將任務狀態更新為指定值（僅當未關閉時才更新）
     * @return true=有更新、false=無符合條件
     */
    boolean markStatus(Long jobId, String status);

    /**
     * 將任務狀態更新為指定值（僅當未關閉且當前狀態 in expected）才更新，避免錯誤覆寫
     * @return true=有更新、false=無符合條件
     */
    boolean markStatusIfIn(Long jobId, String status, List<String> expectedCurrentStatuses);

    /**
     * （快閃補關用）若任務仍未關閉，原子地將其標記為 SECOND_DONE 並關閉。
     * - monitor 在觸發 SECOND 後記 baseline；若之後只看到 IDLE 但計數成長 → 呼叫此方法補關。
     */
    boolean markSecondDoneAndCloseIfActive(Long jobId);

    /** 將任務關閉為 DONE（僅當未關閉時） */
    boolean closeAsDone(Long jobId);

    /** 將任務關閉為 FAILED 並記錄原因（僅當未關閉時） */
    boolean fail(Long jobId, String reason);
}
