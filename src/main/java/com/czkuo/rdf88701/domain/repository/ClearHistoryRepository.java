package com.czkuo.rdf88701.domain.repository;

import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClearHistoryRepository {
    int deleteTableDataBeforeTime( String tableName,
                                  String colName,
                                  LocalDateTime clearBefore,
                                  int limit);
    int deleteMqttEventLogBeforeTime(LocalDateTime clearBefore, int limit);

    int deleteMqttMessageLogBeforeTimeByIdDesc(LocalDateTime clearBefore, int limit);

    int deleteMqttEventStatusLogBeforeTime(LocalDateTime clearBefore, int limit);


}
