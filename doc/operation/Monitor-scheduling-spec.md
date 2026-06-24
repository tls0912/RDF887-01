# Monitor 與排程執行規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`application/monitor`、`infra/scheduler`、背景排程與設備監控

## 目的

本文件說明 RDF887-01 專案中背景 Monitor 與 Scheduler 的類型、執行模式與職責邊界。Monitor 主要負責週期性檢查現場狀態、推進任務流程、同步設備狀態、清理資料與觸發補償行為。

## Monitor 類型

| 類型 | 代表類別 | 職責 |
| --- | --- | --- |
| Request Monitor | `CraneRequestMonitor`、`TransferRequestMonitor` | 掃描未接受 request，呼叫 service 轉入後續處理。 |
| Task Monitor Launcher | `GripperTaskMonitorLauncher`、`TransferTaskMonitorLauncher`、`WorkingBeamTaskMonitorLauncher` | 依設備建立獨立排程，避免同一設備上一輪未完成時重入。 |
| Per-device Task Monitor | `*TaskMonitorPerDevice` | 讀取單台設備狀態與任務，推進 handshake state machine。 |
| Process Monitor | `ProcessStateMonitor` | 定期查詢 WIP、拆併、ZIPA、ZIPB 等流程狀態並更新快取。 |
| PLC Health Scheduler | `PlcHealthScheduler` | 檢查 AUTO 模式 PLC 實體連線，斷線時發布事件並嘗試重連。 |
| History / Cleanup Monitor | `ClearHistoryMonitor`、`HistoryFlushMonitor` | 定期清理或搬移歷史資料。 |
| Feature Monitor | `Ocr*Monitor`、`Safety*Monitor`、`Strapping*Monitor`、`Tt*Monitor` | 特定設備或功能的背景監控。 |

## 執行模型

### Spring Scheduled

```text
@Scheduled(...)
  -> Monitor.trigger / tick / poll
  -> application service / repository / cache
```

適用於單一固定工作，例如 request 掃描、流程狀態查詢、健康檢查與資料清理。

### Per-device Launcher

```text
@PostConstruct
  -> 讀取設備 registry
  -> 每台設備建立 scheduleWithFixedDelay
  -> AtomicBoolean 防止同設備重入
  -> MonitorPoolDispatcher.submit(...)
  -> *TaskMonitorPerDevice
```

適用於多台同型設備，每台設備需要獨立節奏，但不能讓同一台設備的上一輪與下一輪重疊。

## 職責邊界

Monitor 應負責：

- 週期性觸發。
- 取得必要快取或查詢結果。
- 呼叫 service、state machine 或 repository。
- 捕捉例外並記錄 log，避免排程中止。
- 以最小邏輯推進狀態或任務。

Monitor 不應負責：

- 實作大量 SQL。
- 直接組複雜業務規則。
- 繞過既有 service / state machine 直接操作任務生命週期。
- 長時間阻塞同一排程執行緒。
- 在沒有防重入控制的情況下對同一設備並行執行。

## 防重入規則

多設備 launcher 使用 `AtomicBoolean` 作為每台設備的執行旗標：

```text
false -> true：本輪取得執行權
true：上一輪尚未完成，本輪跳過
finally：重設為 false
```

新增 per-device monitor 時，應沿用此模式，避免同一台設備同時推進兩次 handshake 或任務狀態。

## 維護規則

1. 新增 monitor 前，先判斷是單一排程還是 per-device launcher。
2. 排程間隔需可讀且可追蹤；若使用設定檔，需提供合理預設值。
3. Monitor 內應保留 try/catch，避免單次例外使排程停止。
4. 任務推進應交給 service 或 state machine。
5. 清理類 monitor 必須限制每批筆數，避免長時間鎖表。
6. 修改 Monitor 或 Scheduler 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
