package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.application.dto.command.CraneRequestCreateCommand;
import com.czkuo.rdf88701.infra.entity.CraneRequest;

import java.time.LocalDateTime;

/**
 * CraneRequest 工廠：建立初始請求實體
 */
public class CraneRequestFactory {

    /**
     * 建立一筆新的請求實體（初版 version = 1）
     *
     * @param command 外部傳入的建立指令
     * @return 初始化完成的實體
     */
    public static CraneRequest create(CraneRequestCreateCommand command) {
        CraneRequest entity = new CraneRequest();
        entity.setRequestKey(command.getRequestKey());
        entity.setRequestType(command.getRequestType());
        entity.setRequestSource(command.getRequestSource());
        entity.setSourceRequestRef(command.getSourceRequestRef());
        entity.setContainerMainId(command.getContainerMainId());
        entity.setSourceLocationId(command.getSourceLocationId());
        entity.setTargetLocationId(command.getTargetLocationId());
        entity.setOperator(command.getOperator());
        entity.setRemark(command.getRemark());
        entity.setRawPayload(command.getRawPayload());

        entity.setRequestTime(LocalDateTime.now());
        entity.setVersion(1); // 初始版本
        entity.setAccepted("N");
        return entity;
    }

    /**
     * 根據前一筆請求升版（version + 1）
     *
     * @param previous 原始請求
     * @param newRemark 升版備註（可選）
     * @return 新版本請求
     */
    public static CraneRequest upgradeFrom(CraneRequest previous, String newRemark) {
        CraneRequest entity = new CraneRequest();
        entity.setRequestKey(previous.getRequestKey());
        entity.setRequestType(previous.getRequestType());
        entity.setRequestSource(previous.getRequestSource());
        entity.setSourceRequestRef(previous.getSourceRequestRef());
        entity.setContainerMainId(previous.getContainerMainId());
        entity.setSourceLocationId(previous.getSourceLocationId());
        entity.setTargetLocationId(previous.getTargetLocationId());
        entity.setOperator(previous.getOperator());
        entity.setRawPayload(previous.getRawPayload());

        entity.setRequestTime(LocalDateTime.now());
        entity.setVersion(previous.getVersion() + 1);
        entity.setAccepted("N");
        entity.setRemark(newRemark != null ? newRemark : previous.getRemark());
        return entity;
    }
}
