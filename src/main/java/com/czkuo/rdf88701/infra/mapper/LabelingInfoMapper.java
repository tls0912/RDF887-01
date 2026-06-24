package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.LabelingInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-08-23
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Mapper
public interface LabelingInfoMapper extends BaseMapper<LabelingInfo> {

    // 基礎擴充
    LabelingInfo findByRequestKey(@Param("requestKey") String requestKey);
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    // Watermark / Claim
    Long selectMaxId();

    LabelingInfo selectReadyForClaim(@Param("siteCode") String siteCode);

    int bindToSiteAndContainer(@Param("id") Long id,
                               @Param("siteCode") String siteCode,
                               @Param("containerMainId") Long containerMainId,
                               @Param("labelNo") Integer labelNo);

    LabelingInfo selectReadyAfterId(@Param("siteCode") String siteCode,
                                    @Param("afterId") Long afterId);

    LabelingInfo findReady(@Param("containerMainId") Long containerMainId,
                           @Param("siteCode") String siteCode);
}
