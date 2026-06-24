# WebSocket 即時推送規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`presentation/websocket`、狀態監控推播、前端即時訂閱

## 目的

本文件說明 RDF887-01 專案中 WebSocket 的連線端點、訂閱主題、推送資料格式與維護規則。WebSocket 主要用於將 PLC 裝置狀態、PLC 指令狀態、庫位狀態與容器追蹤狀態即時推送給前端。

## 基礎設定

| 項目 | 設定 |
| --- | --- |
| WebSocket endpoint | `/ws` |
| SockJS | 啟用 |
| Simple broker prefix | `/topic` |
| Client send prefix | `/app` |
| 主要設定類別 | `WebSocketConfig` |
| 主要推播服務 | `WebSocketPushService` |

前端通常連線至 `/ws`，並依照需要訂閱 `/topic/...` 主題。此專案目前以後端主動推播為主，前端送入 `/app` 的使用情境較少。

## 推送流程

```text
Monitor / Service / Listener
  -> 組成 presentation.websocket.dto.*
  -> WebSocketPushService
  -> SimpMessagingTemplate.convertAndSend(...)
  -> /topic/... broker
  -> Frontend subscriber
```

推送服務只負責將已組好的 DTO 發送到指定 topic。資料來源、PLC 狀態判斷、任務狀態轉換與快照建立，應保留在 application 或 domain 相關服務中。

## Topic 命名規則

Topic 以設備或資料類別作為第一層，再區分 `status` 或 `command`，批次資料以 `/batch` 結尾。

```text
/topic/{device}/status
/topic/{device}/status/batch
/topic/{device}/command
/topic/{device}/command/batch
```

目前主要 topic：

| 類別 | 單筆狀態 | 批次狀態 | 單筆指令 | 批次指令 |
| --- | --- | --- | --- | --- |
| Crane | `/topic/crane/status` | `/topic/crane/status/batch` | `/topic/crane/command` | `/topic/crane/command/batch` |
| Gripper | `/topic/gripper/status` | `/topic/gripper/status/batch` | - | `/topic/gripper/command/batch` |
| WorkingBeam | `/topic/working-beam/status` | `/topic/working-beam/status/batch` | `/topic/working-beam/command` | `/topic/working-beam/command/batch` |
| Transfer | `/topic/transfer/status` | `/topic/transfer/status/batch` | `/topic/transfer/command` | `/topic/transfer/command/batch` |
| Strapping | `/topic/strapping/status` | `/topic/strapping/status/batch` | `/topic/strapping/command` | `/topic/strapping/command/batch` |
| Site | `/topic/site/status` | `/topic/site/status/batch` | `/topic/site/command` | `/topic/site/command/batch` |
| Infrared | `/topic/infrared/status` | `/topic/infrared/status/batch` | `/topic/infrared/command` | `/topic/infrared/command/batch` |
| Location Point | `/topic/location/point/status` | - | - | - |
| Location Tracking | `/topic/location/tracking/status` | - | - | - |

## DTO 命名規則

WebSocket DTO 放在 `presentation/websocket/dto`。

| 類型 | 命名 |
| --- | --- |
| 單筆設備狀態 | `{Device}StatusUpdatedMessage` |
| 批次設備狀態 | `{Device}StatusBatchMessage` |
| 單筆指令狀態 | `{Device}CommandUpdatedMessage` |
| 批次指令狀態 | `{Device}CommandBatchMessage` |
| 庫位狀態 | `LocationPointStatusMessage` |
| 容器位置追蹤 | `LocationTrackingStatusMessage` |

DTO 欄位應以目前前端顯示與狀態判斷需要為準，不應直接暴露底層 PLC word 或資料庫內部欄位名稱。若欄位代表快照時間，建議使用 `Instant` 或明確命名為 `snapshotTime` / `timestamp`。

## 維護規則

1. 新增設備或狀態推送時，先建立明確 DTO，再新增 `WebSocketPushService` 方法。
2. 新增 topic 時，需同步補本文件 topic 表。
3. 推送方法應捕捉例外並寫入 log，避免單次推送失敗影響監控排程。
4. 批次 topic 應使用 `*BatchMessage`，並以清楚集合欄位表示資料內容。
5. 不在 WebSocket 層做業務判斷；WebSocket 層只負責對外格式與傳輸。
6. 修改 WebSocket DTO 或 topic 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
