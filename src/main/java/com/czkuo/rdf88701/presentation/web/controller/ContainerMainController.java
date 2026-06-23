package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.query.ContainerMainQuery;
import com.czkuo.rdf88701.application.service.query.ContainerMainQueryService;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
