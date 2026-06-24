package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 虛擬容器-屬性對應表（可彈性擴充各式欄位） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-24
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface ContainerAttrMapper extends BaseMapper<ContainerAttr> {

    /** 單筆 upsert：依賴唯一鍵 (container_main_id, attr_key) */
    int upsert(@Param("e") ContainerAttr e);

    /** 批次 upsert：一次寫多筆 */
    int batchUpsert(@Param("list") List<ContainerAttr> list);

    // 新增
    List<Long> selectContainerIdsByAttrKey(@Param("attrKey") String attrKey);

    List<Long> selectContainerIdsByAttrKeyAndValue(@Param("attrKey") String attrKey,
                                                   @Param("attrValue") String attrValue);

    /** 回傳 1 或 null */
    Integer existsByContainerIdAndKey(@Param("containerId") Long containerId,
                                      @Param("attrKey") String attrKey);

    int deleteByAttrKeyAndValue(@Param("attrKey") String attrKey,
                                @Param("attrValue") String attrValue);

    int deleteByContainerIdsAndKeys(@Param("ids") List<Long> containerMainIds,
                                    @Param("keys") List<String> keys);
}
