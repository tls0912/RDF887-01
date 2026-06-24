package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface ContainerDataMapper extends BaseMapper<ContainerData> {

    /**
     * Upsert by container_main_id（XML 對應 <insert id="upsert">）
     * @param entity 需至少包含 containerMainId，其餘欄位可為 null
     * @return 受影響筆數
     */
    int upsert(ContainerData entity);
}
