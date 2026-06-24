package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.ContainerMainQuery;
import com.czkuo.rdf88701.application.service.query.ContainerMainQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 容器主資料分頁查詢 Controller。
 *
 * <p>提供 `/api/v1/container` 查詢入口，將 query object 交給
 * ContainerMainQueryService 取得 PageResult。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@RestController
@RequestMapping("/api/v1/container")
public class ContainerMainController {

    private final ContainerMainQueryService containerMainQueryService;

    public ContainerMainController(ContainerMainQueryService containerMainQueryService) {
        this.containerMainQueryService = containerMainQueryService;
    }

    @GetMapping
    public PageResult<ContainerMain> query(ContainerMainQuery query) {
        return containerMainQueryService.queryPage(query);
    }
}
