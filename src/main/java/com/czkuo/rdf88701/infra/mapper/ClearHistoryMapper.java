package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.application.dto.GripperTaskWithContainerDTO;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-05-06
 */
@Mapper
public interface ClearHistoryMapper {

    int deleteTableDataBeforeTime(@Param("tableName") String tableName,
                                  @Param("colName") String colName,
                                  @Param("clearBefore") LocalDateTime clearBefore,
                                  @Param("limit") int limit);

    int deleteMqttEventLogBeforeTime(@Param("clearBefore") LocalDateTime clearBefore,
                                     @Param("limit") int limit);

    int deleteMqttMessageLogBeforeTimeByIdDesc(@Param("clearBefore") LocalDateTime clearBefore,
                                     @Param("limit") int limit);

    int deleteMqttEventStatusLogBeforeTime(@Param("clearBefore") LocalDateTime clearBefore,
                                     @Param("limit") int limit);

}
