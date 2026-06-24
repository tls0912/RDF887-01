# OCR 整合規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`infra/ocr`、`application/service/ocr`、OCR Controller、OCR Monitor

## 目的

本文件說明 RDF887-01 與 OCR 廠商系統的整合邊界、任務建立、狀態查詢、圖片取得、設備狀態與回呼事件處理規則。OCR 整合的核心原則是：application 層負責任務協調與狀態落地，infra 層只負責 HTTP 呼叫廠商 API。

## 主要元件

| 元件 | 職責 |
| --- | --- |
| `OcrVendorHttpClient` | 呼叫 OCR 廠商 HTTP API，處理 URL 正規化、逾時、header 與錯誤 log。 |
| `OcrCommandService` | 封裝 MCS 對 OCR 的主動命令，例如建立任務、查狀態、取圖片、查設備狀態與警報。 |
| `OcrTaskCoordinator` | 建立本地 `ocr_task`，避免同一容器重複派送未完成任務，並呼叫廠商建立 OCR 任務。 |
| `OcrEventService` | 處理 OCR 回呼事件，更新 `ocr_task`、`ocr_device`、`ocr_alarm`。 |
| `OcrImageService` | OCR 圖片資料服務。 |
| `OcrManualLogService` | OCR 人工判定紀錄服務。 |

## 主動命令流程

```text
Controller / Monitor
  -> OcrTaskCoordinator.createAndDispatch(...)
  -> 建立本地 ocr_task = QUEUED
  -> OcrCommandService.createTask(...)
  -> OcrVendorHttpClient POST /api/v1/ocr-tasks
  -> 依廠商 accepted 更新 DISPATCHED 或 FAILED
```

同一 `container_main_id` 若已有未完成 OCR 任務，`OcrTaskCoordinator` 會避免重複派送。

## 廠商 HTTP API

`OcrVendorHttpClient` 目前封裝下列 API：

| Method | Path | 用途 |
| --- | --- | --- |
| `POST` | `/api/v1/ocr-tasks` | 建立 OCR 任務。 |
| `GET` | `/api/v1/ocr-tasks/{taskId}` | 查詢任務狀態。 |
| `GET` | `/api/v1/ocr-tasks/{taskId}/image` | 取得單張圖片 base64。 |
| `GET` | `/api/v1/ocr-tasks/{taskId}/images` | 取得多張圖片 base64。 |
| `GET` | `/api/v1/ocr-devices/{id}/status` | 查詢 OCR 設備狀態。 |
| `GET` | `/api/v1/ocr-devices/{id}/alarms` | 查詢 OCR 設備警報。 |

## 回呼事件流程

```text
OCR vendor callback
  -> Controller
  -> OcrEventService
  -> ocr_task / ocr_device / ocr_alarm
```

回呼處理原則：

- `TaskStarted`：更新已存在任務為 `RUNNING`，刷新設備為 `BUSY`。
- `TaskCompleted`：依回呼狀態更新 `SUCCESS` 或 `FAILED`，保存 OCR 結果或錯誤訊息。
- `DeviceStatusChanged`：更新設備狀態與是否可接案。
- `AlarmRaised`：新增 ACTIVE 警報；若 repository 未註冊，僅記錄 log。

## 維護規則

1. HTTP 呼叫廠商 API 應集中在 `OcrVendorHttpClient`。
2. 本地任務建立與防重複派送應集中在 `OcrTaskCoordinator`。
3. 回呼事件只透過 `OcrEventService` 更新資料表。
4. 任務狀態集合若調整，需同步確認 coordinator、event service、mapper 與前端查詢。
5. 修改 OCR DTO、service 或 callback 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
