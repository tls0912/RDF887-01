package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.application.dto.GripperTaskWithContainerDTO;
import com.czkuo.rdf88701.application.dto.query.GripperTaskWithContainerQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.domain.repository.GripperTaskRepository;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import com.czkuo.rdf88701.infra.mapper.GripperTaskMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Gripper 任務查詢服務（包含容器資訊）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
public class GripperTaskQueryService {

    private final GripperTaskMapper gripperTaskMapper;
    private final GripperTaskRepository gripperTaskRepository;

    public GripperTaskQueryService(GripperTaskMapper gripperTaskMapper,
                                   GripperTaskRepository gripperTaskRepository) {
        this.gripperTaskMapper = gripperTaskMapper;
        this.gripperTaskRepository = gripperTaskRepository;
    }

    /**
     * 分頁查詢 Gripper 任務（包含容器資訊）
     *
     * @param query 查詢條件
     * @return 分頁結果
     */
    public PageResult<GripperTaskWithContainerDTO> queryWithContainer(GripperTaskWithContainerQuery query) {
        // 啟用分頁
        PageHelper.startPage(query.getSafePageNum(), query.getSafePageSize());

        // 執行查詢
        List<GripperTaskWithContainerDTO> list = gripperTaskMapper.selectWithContainerByCondition(
                query.getGripperId(),
                query.getTaskStatus(),
                query.getCreatedAfter(),
                query.getCreatedBefore()
        );

        // 包裝結果
        PageInfo<GripperTaskWithContainerDTO> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                pageInfo.getList()
        );
    }

    /**
     * 查詢單一 Gripper 裝置下，優先處理的任務（DISPATCHED > PENDING，priority DESC）
     *
     * @param gripperId Gripper 裝置 ID
     * @return 最優先任務（若有）
     */
    public Optional<GripperTask> findTopPriorityTaskByGripper(int gripperId) {
        return gripperTaskRepository.findTopTaskByGripperOrdered(gripperId);
    }
}
