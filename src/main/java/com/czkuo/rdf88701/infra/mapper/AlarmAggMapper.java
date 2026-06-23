package com.czkuo.rdf88701.infra.mapper;

import com.czkuo.rdf88701.application.dto.report.Alarm.TimelineBucketRow;
import com.czkuo.rdf88701.application.dto.report.Alarm.TopRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlarmAggMapper {

    /**
     * 時間序列統計（Trigger/Clear per bucket）
     * @param from  起始（含）
     * @param to    結束（不含）
     * @param type  "ALL"|"ALARM"|"WARNING"
     * @param equipments  篩選設備（可為 null/空）
     * @param bucket "hour"|"day"
     */
    List<TimelineBucketRow> selectTimeline(@Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("type") String type,
                                           @Param("equipments") List<String> equipments,
                                           @Param("bucket") String bucket);

    /**
     * 依次數排名（只計 TRIGGER）
     * @param from 起始（含）
     * @param to   結束（不含）
     * @param type "ALL"|"ALARM"|"WARNING"
     * @param equipments 篩選設備（可為 null/空）
     * @param limit 回傳筆數上限
     */
    List<TopRow> selectTopByCount(@Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  @Param("type") String type,
                                  @Param("equipments") List<String> equipments,
                                  @Param("limit") int limit);
}
