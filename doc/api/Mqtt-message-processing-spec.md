# MQTT 訊息處理規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`application/mqtt`、`infra/mqtt`、`domain/event`、MQTT 相關設定

## 目的

本文件說明 RDF887-01 專案中 MQTT 訊息的接收、分派、處理、回覆、送出與 ACK 追蹤規則。目標是讓新增 `CMD_ID`、追查訊息流程、或調整重送與逾時邏輯時，可以快速找到正確修改點。

## 主要角色

| 元件 | 主要職責 |
| --- | --- |
| `MqttMessageReceivedEvent` | 表示 infra 層已收到 MQTT 訊息，交由 application 層處理。 |
| `MqttMessageEventListener` | 接收入站事件、整理 payload、執行入站去重，並轉交 Router。 |
| `MqttCommandRouter` | 解析 `CMD_ID` 與訊息型別，分派給對應 Handler。 |
| `MqttMessageHandler` | 所有 MQTT Handler 的共用介面。 |
| `AbstractCommandHandler` | COMMAND 類訊息的抽象基底，負責 CMD_ID 比對、TID 驗證與 payload 反序列化。 |
| `AbstractAckHandler` | ACK 類訊息的抽象基底，負責 ACK 的 CMD_ID 比對、TID 驗證與反序列化。 |
| `MqttMessageEventPublisher` | 將要送出的 MQTT 訊息包成 Spring Event，避免業務處理直接依賴底層傳輸。 |
| `MqttAckTimeoutWorker` | 掃描等待 ACK 逾時的 outbox 事件，標記為失敗。 |

## 入站訊息流程

```text
MQTT client / infra mqtt
  -> MqttMessageReceivedEvent
  -> MqttMessageEventListener
  -> MqttCommandRouter
  -> MqttMessageTypeResolver
  -> AbstractCommandHandler / AbstractAckHandler
  -> 具體 CMD_ID Handler
  -> application service / repository / publisher
```

處理重點：

1. Listener 先將 payload 解析為 JSON，轉成單行格式，降低後續比對與記錄差異。
2. Listener 會使用 `InboundDedupRegistry` 進行入站去重，避免同一來源、同一 `CMD_ID`、同一 `TID` 的相同 payload 在短時間內重複處理。
3. Router 以 `CMD_ID + MessageType` 建立 handler key，例如 `S001::COMMAND` 或 `S001::ACK`。
4. Router 若判斷訊息型別為 `UNKNOWN`，不分派給 Handler。
5. ACK 訊息會先檢查 pending 狀態；若為本系統等待中的 ACK，會清除 pending 並標記 outbox 已收到 ACK。

## 出站訊息流程

```text
具體 Handler / Service
  -> MqttMessageEventPublisher.publish(...)
  -> MqttMessageSendEvent
  -> infra mqtt sender / outbox
  -> MQTT broker
  -> 目標系統
```

處理重點：

- 業務層只發布 `MqttMessageSendEvent`，不直接操作 MQTT client。
- 送出事件需帶入 `system`、`payload`、`type`、`tid`、`cmdId`。
- 需要等待 ACK 的訊息，應由 outbox / pending registry 記錄追蹤狀態。

## Handler 新增規則

新增一個 `CMD_ID` Handler 時，需確認：

1. 建立對應的 command 或 ack payload DTO。
2. COMMAND 類處理器繼承 `AbstractCommandHandler<T>`。
3. ACK 類處理器繼承 `AbstractAckHandler<T>`。
4. `getCmdIdInternal()` 必須回傳固定 `CMD_ID`。
5. `getCommandType()` 或 `getAckType()` 必須對應正確 DTO。
6. 若同一 Handler 支援多種訊息型別，需覆寫 `getSupportedTypes()`。
7. Handler 應維持單一職責：解析後的業務動作交給 service，跨系統送出交給 publisher。

## TID 與 CMD_ID 規則

- `CMD_ID` 是 Router 分派 Handler 的主鍵，缺失時直接忽略。
- `TID` 是同一筆指令與 ACK 的關聯識別，抽象 Handler 會檢查格式。
- COMMAND 與 ACK 的 `CMD_ID` 必須一致，才能讓 pending / outbox 正確結案。
- Handler 不應自行猜測缺失的 `CMD_ID` 或 `TID`，避免誤處理外部訊息。

## ACK 與逾時規則

- Router 收到 ACK 時，會先嘗試從 `PendingSendRegistry` 判斷該 ACK 是否屬於等待中的送出事件。
- 若為 pending 訊息，會完成 pending 並呼叫 outbox 標記 ACK。
- `MqttAckTimeoutWorker` 只負責將已送出且等待 ACK 逾時的事件標記為失敗。
- 逾時 Worker 不負責重送；重送或補償應由其他 outbox retry 或業務流程處理。

## 維護注意事項

- Router 的分派 key 規則不可任意改動，否則既有 Handler 會無法註冊或被覆蓋。
- Listener 的去重邏輯若改成直接 `return`，需確認外部系統是否會重送同一 `TID`。
- Handler 內避免直接操作底層 MQTT client，應透過 `MqttMessageEventPublisher`。
- 若調整 payload DTO 欄位，需同步確認外部 MQTT 規格、Handler 反序列化與測試資料。
- 修改 MQTT 主線後至少執行 `.\mvnw.cmd -DskipTests compile`。
