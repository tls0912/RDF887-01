# PLC Adapter 整合規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`infra/adapter/plc`、`config/plc`、`src/main/resources/plc-config`

## 目的

本文件說明 RDF887-01 專案中 PLC Adapter 的分層責任、連線管理、協議轉接、點位設定與寫入元件規則。PLC Adapter 層的目標是把不同 PLC 協議與底層 client 包裝成一致介面，讓 application / monitor / service 不直接依賴特定廠牌或通訊實作。

## 主要目錄

```text
infra/adapter/plc/
├── connection/       # PLC 裝置初始化、連線狀態、port 重試與熔斷策略
├── dto/              # PLC bit/word command/status 傳輸物件
├── protocol/         # 協議抽象、工廠、MC 協議實作與 options mapping
└── writer/           # 各設備類型的 bit/word 寫入服務
```

PLC 點位與裝置設定位於：

```text
src/main/resources/plc-config/
```

## 核心元件職責

| 元件 | 職責 |
| --- | --- |
| `PlcClientManager` | 管理所有 PLC 裝置的 adapter、狀態、初始化、連線 failover 與安全執行入口。 |
| `PlcDeviceStatus` | 保存單一裝置的即時連線狀態、模式、輪詢意圖與錯誤資訊。 |
| `PlcConnectionStrategyManager` | 管理 device:port 的失敗次數、暫時熔斷與可用 port 篩選。 |
| `PlcProtocolAdapter` | 定義統一讀寫介面，包含 boolean、byte、int、float、string 等資料型別。 |
| `ConnectablePlcProtocolAdapter` | 擴充 connect、disconnect、isConnected，供需要手動連線控制的協議使用。 |
| `PlcProtocolAdapterFactory` | 依裝置設定建立協議 adapter，目前支援 `mc`，並保留其他協議擴充點。 |
| `McProtocolAdapter` | 包裝 `McPLC`，實作專案統一的 PLC 讀寫介面。 |
| `PlcOptionMapper` | 將 YAML options 的 kebab-case map 轉成 Java options POJO。 |

## 初始化流程

```text
plc-config/*.yml
  -> PlcProperties / PlcDeviceRegistry
  -> PlcClientManager.initAllDevices(...)
  -> PlcClientManager.initDevice(...)
  -> PlcProtocolAdapterFactory.getOrCreateAdapter(...)
  -> McProtocolAdapter.connect()
  -> PlcDeviceStatus markConnected / markDisconnected
```

初始化時只處理啟用中的裝置。每個裝置會建立狀態物件，並依設定的 IP、protocol、port 或 ports 嘗試連線。

## 連線與 failover 規則

1. 裝置可設定多個 port；`PlcClientManager` 會依序嘗試可用 port。
2. `PlcConnectionStrategyManager` 會記錄每個 `deviceName:port` 的失敗次數。
3. 失敗次數達上限且仍在冷卻時間內時，該 port 會暫時熔斷。
4. 若所有 port 失敗，會建立 fallback adapter，並將裝置狀態標記為斷線。
5. `executeIfAllowed` 執行讀寫前會檢查 connection mode、adapter 是否存在、實體連線是否存在。

## 協議抽象

上層應依賴 `PlcProtocolAdapter`，不要直接依賴 `McPLC`。目前 `PlcProtocolAdapterFactory` 只實作 `mc` 協議：

```text
protocol = "mc"
  -> McOptions
  -> EMcSeries
  -> McPLC
  -> McProtocolAdapter
```

若未來新增 Modbus、OPC UA 或其他協議，應新增對應 adapter，並在 factory 中增加 protocol 分支。

## 點位設定檔

目前主要 PLC 設定檔：

| 設定檔 | 用途 |
| --- | --- |
| `plc-devices.yml` | PLC 裝置連線、protocol、ip、port、options 與啟用設定。 |
| `plc-crane.yml` | 天車點位。 |
| `plc-gripper.yml` | 夾爪點位。 |
| `plc-transfer.yml` | 移載點位。 |
| `plc-working-beam.yml` | 工作樑點位。 |
| `plc-infrared-distance.yml` | 紅外線測距點位。 |
| `plc-site.yml` | 站點點位。 |
| `plc-safety.yml` | 安全設備點位。 |
| `plc-strapping.yml` | 綁帶機點位。 |
| `plc-labeling.yml` | 貼標點位。 |
| `plc-ocr.yml` | OCR 點位。 |
| `plc-aoi.yml` | AOI 點位。 |

## 維護規則

1. 新增 PLC 裝置時，先補 `plc-devices.yml`，再補對應設備點位 yml。
2. 新增點位時，需同步檢查 domain PLC state、memory layout service、writer 或 monitor 是否需要調整。
3. 新增協議時，需實作 `PlcProtocolAdapter` 或 `ConnectablePlcProtocolAdapter`，並在 `PlcProtocolAdapterFactory` 註冊。
4. 上層讀寫 PLC 應透過 `PlcClientManager.executeIfAllowed` 或既有 writer/service，避免繞過連線模式與實體連線檢查。
5. 修改 PLC adapter 或設定載入後，至少執行 `.\mvnw.cmd -DskipTests compile`。
