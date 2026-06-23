package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.application.dto.command.TransferRequestCreateCommand;
import com.czkuo.rdf88701.infra.entity.TransferRequest;

import java.time.LocalDateTime;

/**
 * TransferRequest 工廠：建立初始請求實體
 */
public class TransferRequestFactory {

    /**
     * 建立一筆新的請求實體（初版 version = 1）
     *
     * @param command 外部傳入的建立指令
     * @return 初始化完成的實體
     */
    public static TransferRequest create(TransferRequestCreateCommand command) {
        TransferRequest entity = new TransferRequest();
        entity.setRequestKey(command.getRequestKey());
        entity.setRequestSource(command.getRequestSource());
        entity.setTransferId(command.getTransferId());
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
     * 根據前一筆請求升版（version + 1）
     *
     * @param previous 原始請求
     * @param newRemark 升版備註（可選）
     * @return 新版本請求
     */
    public static TransferRequest upgradeFrom(TransferRequest previous, String newRemark) {
        TransferRequest entity = new TransferRequest();
        entity.setRequestKey(previous.getRequestKey());
        entity.setRequestSource(previous.getRequestSource());
        entity.setTransferId(previous.getTransferId());
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
        entity.setCreatedTime(previous.getCreatedTime()); // 繼承原始建立時間
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }
}
