package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class AlarmActionLogRow {
    public AlarmActionLogRow(String triggerTime,
                             Long globalCode,
                             String itemType,
                             String equipment,
                             String titleZh,
                             String actionNote,
                             String aseCheck,
                             String importTime,
                             Long allCnt,
                             Long cnt) {
        TriggerTime = triggerTime;
        GlobalCode = globalCode;
        ItemType = itemType;
        Equipment = equipment;
        TitleZh = titleZh;
        ActionNote = actionNote;
        AseCheck = aseCheck;
        ImportTime = importTime;
        AllCnt = allCnt;
        Cnt = cnt;
    }

    String TriggerTime;
    Long GlobalCode;
    String ItemType;
    String Equipment;
    String TitleZh;
    String ActionNote;
    String AseCheck;
    String ImportTime;
    Long AllCnt;
    Long Cnt;


}
