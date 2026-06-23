package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ContainerMain 倉儲介面
 */
public interface ContainerMainRepository {

    // ===== 基本 CRUD =====
    Optional<ContainerMain> findById(Long id);

    Optional<ContainerMain> findByAliasCode(String aliasCode);

    Optional<ContainerMain> findByContainerCode(String containerCode);

    Optional<ContainerMain> findByLotNo(String lotNo);

    Optional<ContainerMain> findWithLatestDataById(Long id);

    boolean save(ContainerMain entity);

    boolean update(ContainerMain entity);

    boolean deleteById(Long id);

    List<ContainerMain> findAll();

    // ===== 分頁 / 查詢（MyBatis-Plus）=====
    /**
     * 依關鍵字分頁查詢（關鍵字會比對：alias_code / container_code / lot_no / part_no）
     * @param query 可為 null/blank 表示不過濾
     * @param page  1-based page（若傳 0 會自動轉為 1）
     * @param size  每頁大小（<1 會轉為 1）
     * @return 當頁資料清單
     */
    List<ContainerMain> findPageByQuery(String query, long page, long size);

    /**
     * 與 findPageByQuery 對應的總筆數
     */
    long countByQuery(String query);

    /** 只取「在 location_tracking 內」的容器分頁 */
    List<ContainerMain> findTrackedPageByQuery(String query, long page, long size);

    /** 只取「在 location_tracking 內」的容器總數 */
    long countTrackedByQuery(String query);

    // ===== 倉儲場景查詢 =====

    /** 查詢目前在倉儲儲位內的容器（依據 location_tracking + location_point） */
    List<ContainerMain> findAllInWarehouse();

    /** 查詢目前在倉儲儲位內的容器，並帶出 locationId/locationCode（for 自動搬運邏輯） */
    List<ContainerWithLocation> findAllInWarehouseWithLocation();

    /** 查詢所有「正在被處理中」的容器 ID（有未完成任務或未接受請求） */
    Set<Long> findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();

    /** 根據 aliasCode 判斷是否已存在容器（用於帳務補償判斷） */
    boolean existsByAliasCode(String aliasCode);

    // ===== 供拆/併帳務用的擴充 API =====

    /**
     * 由父容器複製產生一顆新容器（僅複製必要欄位；新 id、新建立時間）
     * @param parentId 父容器 id
     * @param newAliasCode 新名稱
     * @return 新容器 id（建立成功）
     */
    Long createFromParent(Long parentId, String newAliasCode);

    /**
     * 更新容器名稱（for 拆分/合併後命名）
     * @param id 容器 id
     * @param newAliasCode 新名稱（例如：808VCCV001_P_1_2）
     * @return 是否更新成功
     */
    boolean updateAliasCode(Long id, String newAliasCode);

    Integer findMaxSplitIndexByBase(String base);

    // ContainerMainRepository.java
    /** 查倉庫內容器（含位置），且【最新】container_data.content_kind = 'ALL_COVER' */
    List<ContainerWithLocation> findAllInWarehouseWithLocationAllCover();

    /** 可指定任意 content_kind（等值） */
    List<ContainerWithLocation> findAllInWarehouseWithLocationByContentKind(String contentKind);

    // ===== 狀態變更 =====

    /** 將容器關閉：state=CLOSED, closed_time=now */
    boolean close(Long id);

    /** 將容器中止：state=ABORTED（可選擇要不要寫 closed_time） */
    boolean abort(Long id);

    /** （可選）重新開啟：state=ACTIVE, closed_time=NULL */
    boolean reopen(Long id);

    /** 通用狀態更新（若 newState=CLOSED 可帶 closedTime；其餘可傳 null） */
    boolean updateState(Long id, String newState, java.time.LocalDateTime closedTime);

}
