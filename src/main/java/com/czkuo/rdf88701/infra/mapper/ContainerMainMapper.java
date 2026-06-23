package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Mapper
public interface ContainerMainMapper extends BaseMapper<ContainerMain> {

    /** 聚合查詢 ContainerMain + 最新一筆 ContainerData */
    ContainerMain selectWithLatestDataById(@Param("id") Long id);

    /** 查詢所有在倉儲儲位中的容器（只回基本欄位） */
    List<ContainerMain> findAllInWarehouse();

    /** 查詢所有在倉儲儲位中的容器，並回傳其目前所在 location_point 的 id/code */
    List<ContainerWithLocation> findAllInWarehouseWithLocation();

    /** 查詢所有尚有未完成任務或未處理請求的 container_main_id */
    List<Long> selectProcessingContainerIds();

    // ======================= 自訂擴充：拆/併帳務 =======================

    /**
     * 只更新容器名稱
     * @return 影響筆數
     */
    int updateAliasCodeById(@Param("id") Long id, @Param("aliasCode") String aliasCode);

    // 取得 base 下的最大拆分尾碼（_k）
    Integer findMaxSplitIndexByBase(String base);

    List<ContainerWithLocation> findAllInWarehouseWithLocationByContentKind(String contentKind);

    int updateStateById(@Param("id") Long id,
                        @Param("state") String state,
                        @Param("closedTime") LocalDateTime closedTime);

}
