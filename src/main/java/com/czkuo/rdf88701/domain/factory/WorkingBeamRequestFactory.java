package com.czkuo.rdf88701.domain.factory;

import com.czkuo.rdf88701.application.dto.command.WorkingBeamRequestCreateCommand;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;

import java.time.LocalDateTime;

/**
 * WorkingBeamRequest 工廠：建立初始請求實體
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class WorkingBeamRequestFactory {

    /**
     * 建立一筆新的請求實體（初版 version = 1）
     *
     * @param command 外部傳入的建立指令
     * @return 初始化完成的實體
     */
    public static WorkingBeamRequest create(WorkingBeamRequestCreateCommand command) {
        WorkingBeamRequest entity = new WorkingBeamRequest();
        entity.setRequestKey(command.getRequestKey());
        entity.setRequestSource(command.getRequestSource());
        entity.setWorkingBeamId(command.getWorkingBeamId());
        entity.setDirection(command.getDirection());
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
    public static WorkingBeamRequest upgradeFrom(WorkingBeamRequest previous, String newRemark) {
        WorkingBeamRequest entity = new WorkingBeamRequest();
        entity.setRequestKey(previous.getRequestKey());
        entity.setRequestSource(previous.getRequestSource());
        entity.setWorkingBeamId(previous.getWorkingBeamId());
        entity.setDirection(previous.getDirection());
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
