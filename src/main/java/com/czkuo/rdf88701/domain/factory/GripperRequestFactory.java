package com.czkuo.rdf88701.domain.factory;


import com.czkuo.rdf88701.application.dto.command.GripperRequestCreateCommand;
import com.czkuo.rdf88701.infra.entity.GripperRequest;

import java.time.LocalDateTime;

/**
 * GripperRequest 工廠：建立初始請求實體
 */
public class GripperRequestFactory {

    /**
     * 建立一筆新的 GripperRequest 實體（初始版本 = 1）
     *
     * @param command 外部傳入的建立指令
     * @return 初始化完成的 GripperRequest
     */
    public static GripperRequest create(GripperRequestCreateCommand command) {
        GripperRequest entity = new GripperRequest();
        entity.setRequestKey(command.getRequestKey());
        entity.setRequestSource(command.getRequestSource());
        entity.setGripperId(command.getGripperId());
        entity.setTaskType(command.getTaskType());
        entity.setSourceLocationId(command.getSourceLocationId());
        entity.setTargetLocationId(command.getTargetLocationId());
        entity.setSourceLocationName(command.getSourceLocationName());
        entity.setTargetLocationName(command.getTargetLocationName());
        entity.setOperator(command.getOperator());
        entity.setRemark(command.getRemark());
        entity.setRawPayload(command.getRawPayload());

        entity.setRequestTime(LocalDateTime.now());
        entity.setVersion(1); // 初始版本
        entity.setAccepted("N");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 根據舊的 GripperRequest 升版（version + 1）
     *
     * @param previous 之前的 GripperRequest
     * @param newRemark 若有升版備註則覆蓋
     * @return 新版本 GripperRequest
     */
    public static GripperRequest upgradeFrom(GripperRequest previous, String newRemark) {
        GripperRequest entity = new GripperRequest();
        entity.setRequestKey(previous.getRequestKey());
        entity.setRequestSource(previous.getRequestSource());
        entity.setGripperId(previous.getGripperId());
        entity.setTaskType(previous.getTaskType());
        entity.setSourceLocationId(previous.getSourceLocationId());
        entity.setTargetLocationId(previous.getTargetLocationId());
        entity.setSourceLocationName(previous.getSourceLocationName());
        entity.setTargetLocationName(previous.getTargetLocationName());
        entity.setOperator(previous.getOperator());
        entity.setRawPayload(previous.getRawPayload());

        entity.setRequestTime(LocalDateTime.now());
        entity.setVersion(previous.getVersion() + 1);
        entity.setAccepted("N");
        entity.setRemark(newRemark != null ? newRemark : previous.getRemark());
        entity.setCreatedTime(previous.getCreatedTime());
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }
}
