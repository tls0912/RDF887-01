# ZIP Stocker 整合規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`infra/zip`、`application/service/zip`、ZIP Stocker Controller、Robot 流程

## 目的

本文件說明 RDF887-01 與 ZIP Stocker 的 HTTP 整合方式。ZIP 整合包含兩個方向：MCS 主動送 Primary 給 ZIP，以及 ZIP 主動送 Primary 事件給 MCS，MCS 回覆 Secondary。

## 主要元件

| 元件 | 職責 |
| --- | --- |
| `ZipRouter` | 依 `ZipTarget` 與設定取得 ZIPA / ZIPB 或 fallback base URL。 |
| `ZipHttpClient` | 統一送出 HTTP POST，處理 URL、逾時、JSON header、泛型 Root 回應與錯誤 log。 |
| `ZipHeaders` | 建立 ZIP 通訊 Header。 |
| `ZipStockerCommandService` | MCS 主動送出 CheckTimer、DispatchOrder、StatusQuery、WipInfoUpdate 等 Primary 命令。 |
| `ZipStockerEventService` | 處理 ZIP 上報事件，更新資料、觸發 MQTT、相機或 Robot 流程，並回覆 Secondary。 |

## MCS 主動命令流程

```text
Service / Monitor
  -> ZipStockerCommandService
  -> Root<PrimaryBody> + Header(Direction=Primary, Sender=MCS)
  -> ZipHttpClient.post(target, path, req, respClass)
  -> ZIP HTTP API
  -> Root<SecondaryBody>
```

目前常見主動命令：

| 命令 | 用途 |
| --- | --- |
| `CheckTimer` | MCS 對 ZIP 校時。 |
| `DispatchOrder` | 發出出貨命令。 |
| `CancelDispatchOrder` | 取消出貨命令。 |
| `PortLockUnlock` | Port 鎖定或解鎖。 |
| `StatusQuery` | 查詢儲格、Port、派貨狀態或庫存水位。 |
| `WipInfoUpdate` | 更新 WIP 資訊。 |

## ZIP 上報事件流程

```text
ZIP HTTP callback
  -> ZipStockerApiController
  -> ZipStockerEventService
  -> repository / MQTT / camera / robot task
  -> Secondary response
```

`ZipStockerEventService` 處理入庫、出庫、狀態回報、讀卡、翻轉、平台輸入等事件。部分事件會同步等待 MQTT ACK 或觸發 Robot 流程，因此此 service 屬於 ZIP 事件編排入口。

## 路由與目標

`ZipRouter` 會依 `ZipTarget` 取得 target-specific base URL。若 target 未配置，會退回舊的 `baseUrl`；若仍無設定，丟出錯誤。

```text
ZipTarget.ZIPA / ZIPB
  -> zipstocker.targets.{zipa|zipb}.base-url
  -> zipstocker.base-url fallback
```

## 維護規則

1. 新增 MCS → ZIP 命令時，優先在 `ZipStockerCommandService` 增加方法。
2. 新增 ZIP → MCS 事件時，優先在 `ZipStockerEventService` 增加處理流程。
3. HTTP 傳輸細節應維持在 `ZipHttpClient`，不要在 service 中直接建立 RestTemplate。
4. ZIP Header 建立應使用 `ZipHeaders`。
5. 涉及 MQTT ACK 等待、相機拍照或 Robot 任務時，需在流程文件同步標註。
6. 修改 ZIP DTO、service 或 controller 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
