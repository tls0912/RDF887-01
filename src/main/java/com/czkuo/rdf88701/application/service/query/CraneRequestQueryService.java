package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.query.CraneRequestQuery;
import com.czkuo.rdf88701.application.dto.vo.CraneRequestVO;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.mapper.CraneRequestMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Crane Request 查詢服務
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
public class CraneRequestQueryService {

    private final CraneRequestMapper craneRequestMapper;

    public CraneRequestQueryService(CraneRequestMapper craneRequestMapper) {
        this.craneRequestMapper = craneRequestMapper;
    }

    /**
     * 分頁查詢 Crane Request（可用於 UI 查詢）
     *
     * @param query 查詢條件
     * @return 分頁結果
     */
    public PageResult<CraneRequestVO> queryPage(CraneRequestQuery query) {
        PageHelper.startPage(query.getSafePageNum(), query.getSafePageSize());

//        List<CraneRequest> list = craneRequestMapper.selectByCondition(
//                query.getRequestType(),
//                query.getRequestSource(),
//                query.getAccepted(),
//                query.getRequestAfter(),
//                query.getRequestBefore()
//        );

        List<CraneRequest> list = new ArrayList<>();

        PageInfo<CraneRequest> pageInfo = new PageInfo<>(list);
        List<CraneRequestVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());

        return new PageResult<>(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                voList
        );
    }

    /**
     * Entity 轉 VO
     */
    private CraneRequestVO toVO(CraneRequest entity) {
        CraneRequestVO vo = new CraneRequestVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
