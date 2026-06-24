# RDF887-01 程式結構地圖

本文件用來快速理解 RDF887-01 後端服務的程式分層、主要模組、資料流與常見修改入口。

## 一、整體分層

```text
外部系統 / 前端 / 現場設備
        |
        v
presentation
  REST Controller / WebSocket / MQTT DTO
        |
        v
application
  Service / Monitor / Listener / Generator / Assembler / MQTT Handler
        |
        v
domain
  領域服務 / PLC 狀態模型 / 任務策略 / Repository 介面 / 事件
        |
        v
infra
  DB Mapper / Repository 實作 / PLC Adapter / MQTT / OCR / ZIP / Serial / EMQX
        |
        v
resources
  mapper XML / application.yml / mqtt.yml / datasource.yml / plc-config/*.yml
```

## 二、根目錄地圖

```text
src/main/java/com/czkuo/rdf88701/
├── Rdf88701Application.java      # Spring Boot 啟動入口
├── application/                  # 應用流程、任務編排、監控與外部訊息處理
├── common/                       # 共用 DTO、常數、列舉、例外、工具
├── config/                       # Spring Bean、執行緒、排程、PLC、MQTT、Serial 設定
├── domain/                       # 領域模型、PLC 狀態、策略、事件與 Repository 介面
├── infra/                        # 基礎設施實作：資料庫、PLC、MQTT、OCR、ZIP、Serial
├── presentation/                 # REST API、WebSocket、對外 DTO
└── tools/                        # 程式碼產生工具
```

## 三、核心 package 說明

### 1. presentation：對外入口

```text
presentation/
├── web/
│   ├── controller/               # REST API Controller
│   ├── converter/                # Web 層資料轉換
│   ├── dto/                      # API request / response DTO
│   └── mapper/                   # Web DTO mapping
├── websocket/                    # WebSocket 設定、推送服務與推送訊息 DTO
└── mqtt/                         # MQTT 對外呈現層 DTO
```

主要 Controller：

- `AuthController`：登入與認證
- `ContainerController`、`ContainerMainController`：料盒與主資料
- `CraneController`、`CraneRequestController`、`ExternalCraneRequestController`：天車設備與請求
- `GripperController`、`GripperTaskController`：夾爪設備與任務
- `TransferController`：移載設備
- `WorkingBeamController`：工作樑設備
- `Location*Controller`：庫位、流程、帳務、追蹤與查詢
- `MqttCommandApiController`：MQTT 命令 API
- `Ocr*Controller`：OCR 命令、驗證、Web API、人工判定紀錄
- `RobotTaskCommandController`、`RobotTaskMonitorController`：R007 / R008 / R029 / R031 流程任務
- `AlarmReportController`、`StrappingReportController`、`TtReportController`：報表
- `ZipStockerApiController`、`ZipStockerCommandApiController`：ZIP Stocker 整合

### 2. application：應用流程與任務編排

```text
application/
├── assembler/                    # 將任務或狀態組成 PLC word command
├── dto/                          # application 層 command / query / vo / report DTO
├── generator/                    # 任務 request 產生器
├── interfaces/                   # application 層依賴介面
├── listener/                     # domain/application 事件監聽
├── monitor/                      # 輪詢、排程、自動流程與設備監控
├── mqtt/                         # MQTT 訊息處理、發布、路由、worker
└── service/                      # 應用服務
```

重點子模組：

- `generator/impl/crane`：天車請求生成
- `generator/impl/gripper`：夾爪請求生成
- `generator/impl/infrared`：紅外線請求生成
- `generator/impl/transfer`：移載請求生成
- `generator/impl/workingbeam`：工作樑請求生成
- `monitor`：自動任務、設備狀態、警報、OCR、綁帶、歷史資料、心跳等監控
- `mqtt/handler`：MQTT ACK 與 command handler
- `mqtt/publisher`：MQTT 訊息發布
- `mqtt/router`：MQTT 訊息路由
- `service/task`：任務相關應用服務
- `service/location`：庫位與帳務相關服務
- `service/report`：報表查詢服務
- `service/ocr`：OCR 業務服務
- `service/zip`：ZIP Stocker 業務整合

### 3. domain：領域模型與規則

```text
domain/
├── dto/                          # 領域資料物件
├── event/                        # 領域事件
├── factory/                      # 領域物件建立
├── plc/                          # PLC command、state、strategy、value object
├── repository/                   # Repository 介面
└── service/                      # 領域服務與策略
```

重點子模組：

- `domain/plc/command`：PLC 命令模型
- `domain/plc/state/common`：共用 PLC 狀態
- `domain/plc/state/crane`：天車狀態
- `domain/plc/state/gripper`：夾爪狀態
- `domain/plc/state/infrared`：紅外線狀態
- `domain/plc/state/safety`：安全設備狀態
- `domain/plc/state/site`：站點狀態
- `domain/plc/state/Strapping`：綁帶機狀態
- `domain/plc/state/transfer`：移載狀態
- `domain/plc/state/workingbeam`：工作樑狀態
- `domain/service/strategy`：流程或任務策略
- `domain/repository`：資料存取抽象，實作通常在 `infra/repository/impl`

### 4. infra：基礎設施與外部系統實作

```text
infra/
├── adapter/                      # 外部設備或系統 adapter
├── cache/                        # 快取
├── decoder/                      # 外部訊息解碼
├── dto/                          # infra 層 DTO
├── emqx/                         # EMQX 管理 API 整合
├── encoder/                      # 外部訊息編碼
├── entity/                       # DB Entity
├── event/                        # infra 事件模型
├── init/                         # 啟動初始化
├── lock/                         # 鎖定與同步控制
├── mapper/                       # MyBatis Mapper interface
├── mqtt/                         # MQTT client 與 infra 實作
├── ocr/                          # OCR vendor 整合
├── repository/                   # Repository 實作
├── scheduler/                    # infra 排程
├── serial/                       # 序列埠整合
├── service/                      # infra service
└── zip/                          # ZIP Stocker HTTP 整合
```

PLC 相關實作集中在：

```text
infra/adapter/plc/
├── connection/                   # PLC 連線
├── dto/                          # PLC adapter DTO
├── protocol/                     # 通訊協定
│   ├── mc/                       # Mitsubishi MC protocol
│   ├── options/                  # protocol options
│   └── support/                  # protocol support
└── writer/                       # PLC 寫入
```

資料庫相關實作集中在：

```text
infra/entity/                     # 資料表 Entity
infra/mapper/                     # MyBatis Mapper interface
infra/repository/impl/            # domain repository 實作
src/main/resources/mapper/        # MyBatis XML SQL
```

### 5. common：共用元件

```text
common/
├── constants/                    # 共用常數
├── dto/                          # 共用 DTO
├── enums/                        # 共用列舉
├── exception/                    # 共用例外
├── image/                        # 影像相關工具或模型
└── util/                         # 工具類
```

### 6. config：系統設定

```text
config/
├── executor/                     # 執行緒池
├── modbus/                       # Modbus 設定
├── mqtt/                         # MQTT Bean 設定
├── plc/                          # PLC 設定載入與 Bean
├── scheduling/                   # 排程設定
├── serial/                       # 序列埠設定
└── zip/                          # ZIP Stocker 設定
```

## 四、資源設定地圖

```text
src/main/resources/
├── application.yml               # 主設定，匯入其他 yml
├── datasource.yml                # MySQL 連線
├── mqtt.yml                      # MQTT broker、topic、心跳、握手、稽核
├── camera.yml                    # 相機設定
├── serial.yml                    # 序列埠設定
├── mybatis-plus.yml              # MyBatis-Plus 設定
├── page-helper.yml               # PageHelper 設定
├── logback-spring.xml            # logging 設定
├── mapper/                       # MyBatis XML mapper
├── plc-config/                   # PLC 設備與點位設定
├── static/                       # 靜態資源
└── templates/                    # 範本
```

PLC 設定檔：

- `plc-devices.yml`：PLC 裝置連線與共用設定
- `plc-gripper.yml`：夾爪點位
- `plc-crane.yml`：天車點位
- `plc-transfer.yml`：移載點位
- `plc-working-beam.yml`：工作樑點位
- `plc-infrared-distance.yml`：紅外線測距點位
- `plc-strapping.yml`：綁帶機點位
- `plc-labeling.yml`：貼標點位
- `plc-ocr.yml`：OCR 點位
- `plc-aoi.yml`：AOI 點位
- `plc-site.yml`：站點點位
- `plc-safety.yml`：安全設備點位

## 五、主要執行流程

### 1. REST API 查詢 / 操作流程

```text
HTTP Request
  -> presentation/web/controller/*Controller
  -> application/service/*
  -> domain/service 或 domain/repository
  -> infra/repository/impl 或 infra/mapper
  -> resources/mapper/*.xml
  -> MySQL
```

### 2. PLC 狀態監控流程

```text
Scheduler / Monitor
  -> application/monitor/*
  -> application/interfaces/PlcSafeAccess 或 domain/service/plc
  -> infra/adapter/plc/*
  -> plc-config/*.yml
  -> PLC device
  -> domain/plc/state/*
  -> application/service 或 websocket push
```

### 3. 任務產生與派發流程

```text
Controller / Monitor / Event
  -> application/generator/*
  -> application/assembler/*
  -> domain/plc/command/*
  -> application/service/command 或 monitor
  -> infra/adapter/plc/writer
  -> PLC
```

### 4. MQTT 訊息流程

```text
MQTT Broker
  -> infra/mqtt 或 application/mqtt/listener
  -> application/mqtt/router
  -> application/mqtt/handler/ack 或 handler/command
  -> application/service/*
  -> infra/mapper 或 publisher
  -> MQTT Broker / DB / WebSocket
```

### 5. WebSocket 推送流程

```text
Monitor / Service / Event Listener
  -> presentation/websocket/WebSocketPushService
  -> presentation/websocket/dto/*
  -> WebSocket clients
```

## 六、常見修改入口

| 需求 | 優先查看 |
| --- | --- |
| 新增 REST API | `presentation/web/controller`、`application/service` |
| 修改任務產生邏輯 | `application/generator`、`application/assembler`、`doc/*flow-spec.md` |
| 修改 PLC 點位 | `src/main/resources/plc-config/*.yml`、`domain/plc/state`、`infra/adapter/plc` |
| 修改 MQTT topic 或心跳 | `src/main/resources/mqtt.yml`、`application/mqtt`、`infra/mqtt` |
| 修改資料表查詢 | `infra/mapper`、`src/main/resources/mapper`、`infra/entity` |
| 修改 WebSocket 推送 | `presentation/websocket`、相關 `application/monitor` |
| 修改 OCR 流程 | `application/service/ocr`、`application/monitor/ocr`、`infra/ocr` |
| 修改 ZIP Stocker 整合 | `application/service/zip`、`domain/dto/zip`、`infra/zip` |
| 修改報表 | `presentation/web/controller/*ReportController`、`application/service/report`、`application/dto/report` |
| 修改自動流程 R007/R008/R029/R031 | `application/monitor/R*Walker.java`、`application/monitor/AutoR*Planner.java`、`doc/R*流程規格.md` |

## 七、命名線索

- `*Controller`：HTTP API 入口
- `*Service`：應用或領域服務
- `*Monitor`：排程輪詢、現場狀態監控、自動流程
- `*Listener`：事件監聽
- `*Generator`：任務或請求生成
- `*Assembler`：將業務資料組成 PLC command 或 word command
- `*Mapper`：MyBatis Mapper 或資料轉換器
- `*Repository`：領域資料存取抽象或實作
- `*Dto` / `*DTO`：資料傳輸物件
- `*VO`：查詢或畫面用資料物件
- `*Command`：命令物件
- `*Query`：查詢條件

## 八、文件對照

流程規格文件位於 `doc/flow/`：

- [GripperRequest-flow-spec.md](../flow/GripperRequest-flow-spec.md)
- [TransferRequest-flow-spec.md](../flow/TransferRequest-flow-spec.md)
- [WorkingBeamRequest-flow-spec.md](../flow/WorkingBeamRequest-flow-spec.md)
- [IR1RequestGenerator-flow-spec.md](../flow/IR1RequestGenerator-flow-spec.md)
- [R007流程規格.md](../flow/R007流程規格.md)
- [R008流程規格.md](../flow/R008流程規格.md)
- [R029流程規格.md](../flow/R029流程規格.md)
- [R031流程規格.md](../flow/R031流程規格.md)

建議修改流程前先讀對應規格，再找 `application/generator` 與 `application/monitor` 中同名或同流程代碼的類別。
