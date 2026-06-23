package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.query.ContainerMainQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.mapper.ContainerMainMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Container 主表查詢服務
 */
@Service
public class ContainerMainQueryService {

    private final ContainerMainMapper containerMainMapper;

    public ContainerMainQueryService(ContainerMainMapper containerMainMapper) {
        this.containerMainMapper = containerMainMapper;
    }

    /**
     * 根據條件查詢 ContainerMain 清單（支援分頁）
     *
     * @param query 查詢條件
     * @return 分頁查詢結果
     */
    public PageResult<ContainerMain> queryPage(ContainerMainQuery query) {
        LambdaQueryHelper<ContainerMain> helper = LambdaQueryHelper.<ContainerMain>of()
                .eqIfPresent(ContainerMain::getId, query::getId)
                .inIfPresent(ContainerMain::getId, query::getIdList)
                .likeIfPresent(ContainerMain::getAliasCode, query::getAliasCode)
                .eqIfPresent(ContainerMain::getContainerType, query::getContainerType)
                .likeIfPresent(ContainerMain::getContainerCode, query::getContainerCode)
                .likeIfPresent(ContainerMain::getLotNo, query::getLotNo)
                .likeIfPresent(ContainerMain::getPartNo, query::getPartNo)
                .geIfPresent(ContainerMain::getCreatedTime, query::getCreatedAfter)
                .leIfPresent(ContainerMain::getCreatedTime, query::getCreatedBefore);

        PageHelper.startPage(query.getSafePageNum(), query.getSafePageSize());
        List<ContainerMain> list = containerMainMapper.selectList(helper.getWrapper());

        PageInfo<ContainerMain> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                pageInfo.getList()
        );
    }
}
