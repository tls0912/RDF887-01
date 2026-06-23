package com.czkuo.rdf88701.application.service.tt;

import com.czkuo.rdf88701.application.dto.report.tt.*;
import com.czkuo.rdf88701.common.dto.PageResult;

import java.util.List;

public interface TtReportService {
    List<TtDeviceSummaryDto> getSummary(TtQueryFilterDto f);
    PageResult<TtRecordRowDto> getRecordsPage(TtQueryFilterDto f, int pageNum, int pageSize);
    List<TtRecordRowGroupIdDto> getSummaryGroupID(TtQueryFilterDto f);
    PageResult<TtRecordRowDto> getRecordsPageGroupId(TtQueryFilterDto f, int pageNum, int pageSize);
    List<TtRecordRowDto> getExportData(TtQueryFilterDto f);
    List<TtRecordItemRowDto> getItems(long recordId);
}
