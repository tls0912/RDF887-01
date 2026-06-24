# PLC Memory Layout 解析規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[PLC Adapter 整合規格](PLC-adapter-spec.md)
- 適用範圍：`infra/service/*MemoryLayoutService`、`config/plc/*Properties`、`src/main/resources/plc-config`

## 目的

本文件說明 PLC 大區塊資料如何依設備 ID、read/write 區段與 B/W 記憶體型別切割成單一設備可使用的資料。Memory Layout Service 位於 infra 層，主要負責資料切割與基本解碼，不負責狀態機判斷、任務派發或業務規則。

## 主要元件

| Service | 設備設定來源 | 對應設定檔 |
| --- | --- | --- |
| `CraneMemoryLayoutService` | `PlcCraneProperties` | `plc-crane.yml` |
| `GripperMemoryLayoutService` | `PlcGripperProperties` | `plc-gripper.yml` |
| `TransferMemoryLayoutService` | `PlcTransferProperties` | `plc-transfer.yml` |
| `WorkingBeamMemoryLayoutService` | `PlcWorkingBeamProperties` | `plc-working-beam.yml` |
| `InfraredMemoryLayoutService` | `PlcInfraredProperties` | `plc-infrared-distance.yml` |
| `SiteMemoryLayoutService` | `PlcSiteProperties` | `plc-site.yml` |
| `StrappingMemoryLayoutService` | `PlcStrappingProperties` | `plc-strapping.yml` |

## 標準資料流

```text
PLC readBytes(...)
  -> fullData byte[]
  -> MemoryLayoutService.extractAreaBytes(...)
  -> PlcDataCodec.bytesToBits / bytesToWords / decodeString
  -> domain plc state / command status / websocket DTO
```

呼叫端通常會先從 PLC 讀取一段較大的 B 區或 W 區資料，再由 Memory Layout Service 依單一設備設定切出該設備自己的區段。

## 區段切割規則

`extractAreaBytes` 的核心輸入：

| 參數 | 說明 |
| --- | --- |
| `deviceId` | 設備 ID，例如 craneId、gripperId、transferId。 |
| `areaType` | 區段類型，通常為 `read` 或 `write`。 |
| `memoryType` | 記憶體型別，目前支援 `B` 與 `W`。 |
| `fullData` | 從 PLC 讀回或準備寫入的大區塊 byte array。 |
| `fullStart` | `fullData` 對應的 PLC 起始位址。 |

B 區以 bit 為單位計算 offset：

```text
bitOffset = area.address - fullStart
offset = bitOffset / 8
lengthBytes = (area.length + 7) / 8
```

W 區以 word 為單位計算 offset：

```text
offset = (area.address - fullStart) * 2
lengthBytes = area.length * 2
```

若 offset 超出 `fullData` 範圍，會丟出 `IndexOutOfBoundsException`，避免讀到錯誤設備資料。

## 解碼方法

| 方法 | 用途 |
| --- | --- |
| `extractBits(...)` | 擷取 B 區並轉成 `boolean[]`。 |
| `extractWords(...)` | 擷取 W 區並轉成 `int[]`。 |
| `extractString(...)` | 擷取 W 區並以 little-endian 字串方式解碼。 |

解碼委派給 `PlcDataCodec`，Memory Layout Service 不自行實作 byte/word/string 的底層轉換。

## 職責邊界

Memory Layout Service 應負責：

- 建立設備 ID 到設定物件的快取。
- 依 `DeviceArea` 設定切割 B/W 區段。
- 呼叫共用 codec 做 bits、words、string 解碼。
- 在設備 ID 不存在、memory type 不支援、資料範圍不正確時丟出明確例外。

Memory Layout Service 不應負責：

- 判斷設備狀態機。
- 決定任務是否完成。
- 寫入資料庫。
- 發送 WebSocket 或 MQTT。
- 直接決定 PLC 讀取範圍。

## 維護規則

1. 新增設備點位時，需確認對應 `*Properties` 與 `*MemoryLayoutService` 的設備 ID 對應表是否仍正確。
2. 修改 B/W 區長度時，需確認呼叫端讀取的 `fullData` 範圍足以涵蓋該區段。
3. 新增 memory type 前，需同步更新所有相關 Memory Layout Service 或抽出共用邏輯。
4. 字串欄位目前以 little-endian 解碼；若設備改用其他端序，需在規格與實作中明確標註。
5. 修改 Memory Layout Service 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
