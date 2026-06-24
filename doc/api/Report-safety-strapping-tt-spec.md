# 報表、Safety、Strapping、TT 規格

## 文件資訊

- 建立日期：2026-06-24
- 適用範圍：報表 Controller、`application/service/report`、`StrappingStatsService`、`application/service/tt`、Safety polling

## 目的

本文件整理報表與現場統計類功能的職責。這些模組以查詢、聚合、統計與狀態彙整為主，不應負責任務派發。

## 報表類型

| 類型 | 主要元件 | 說明 |
| --- | --- | --- |
| Alarm | `AlarmReportHybridService`、`AlarmAggMapper` | timeline、Top N、spans、overview。 |
| Strapping | `StrappingStatsService`、`StrappingReportController` | 依機台與 product_id 統計成功、失敗、異常與通關率。 |
| TT | `TtReportServiceImpl`、`TtReportController` | 設備週期、明細、group id 與匯出資料。 |
| Safety | `SafetyPollingHandler`、`SafetyStatusMonitor` | 解析安全點位、更新快取、發送狀態與過期事件。 |

## 維護規則

1. 報表查詢需限制時間區間、page size 或匯出量。
2. 大量聚合優先放 Mapper 或 SQL；需要配對/狀態演算時可在 Java service 做。
3. Safety polling 不應混入報表邏輯，僅負責安全點狀態快取與事件。
4. Strapping / TT 統計規則變更時，需同步更新本文件與前端欄位說明。
