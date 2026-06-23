package com.czkuo.rdf88701.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * R008 任務主表（可計算欄位 + 狀態機 + 對外結果快取） Mapper 接口
 * </p>
 *
 * @author czkuo
 * @since 2025-10-18
 */
@Mapper
public interface RobotR008TaskMapper extends BaseMapper<RobotR008Task> {
    List<String> selectBinTypeByCarrierId(@Param("carrierId") String carrierId);
}
