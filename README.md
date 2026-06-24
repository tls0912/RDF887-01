# RDF887-01

RDF887-01 是一套以 Spring Boot 建置的現場自動化後端服務，負責串接產線設備、PLC、MQTT、WebSocket 與 MySQL 資料庫，提供任務派發、狀態監控、紀錄查詢與外部系統整合 API。

系統涵蓋天車、夾爪、移載、工作樑、紅外線測距、OCR、相機、綁帶機、貼標、ZIP Stocker、門禁與安全狀態等模組，並以 Monitor / Listener / Generator / Service / Mapper 分層處理設備狀態、命令生成與資料持久化。

## 技術棧

- Java 17
- Spring Boot 3.4.4
- Spring Web / WebSocket / Validation
- MyBatis-Plus 3.5.x
- PageHelper
- MySQL Connector/J
- Eclipse Paho MQTT v5
- jSerialComm
- JNA
- Hikrobot MVS Camera Wrapper
- Logstash Logback Encoder
- Maven Wrapper

## 專案結構

```text
RDF887-01/
├── ddl/                         # 資料庫或結構相關檔案
├── doc/                         # 架構、API、資料存取、流程與維運規格文件
├── libs/                        # 本地第三方 JAR
├── src/
│   ├── main/
│   │   ├── java/com/czkuo/rdf88701/
│   │   │   ├── application/     # 任務編排、監控、命令組裝、事件監聽
│   │   │   ├── domain/          # 領域模型與服務
│   │   │   ├── infrastructure/  # 外部設備、資料庫、MQTT、PLC 等基礎設施
│   │   │   ├── presentation/    # REST API 與 WebSocket
│   │   │   └── tools/           # 程式碼產生工具
│   │   └── resources/
│   │       ├── mapper/          # MyBatis XML Mapper
│   │       ├── plc-config/      # PLC 點位與設備設定
│   │       └── *.yml            # Spring、資料庫、MQTT、相機、序列埠設定
│   └── test/
├── rdf887_01.sql                # MySQL 初始化資料
├── pom.xml
├── mvnw / mvnw.cmd
└── Rdf88701-Service*.bat        # Windows 啟動批次檔
```

## 主要功能

- REST API：任務、設備、庫位、報表、OCR、MQTT、ZIP Stocker 等管理介面
- WebSocket：即時推送設備狀態、命令狀態與任務更新
- PLC 整合：依 `src/main/resources/plc-config/*.yml` 設定讀寫各設備點位
- MQTT 整合：支援 ASE / SEEC 連線、握手、心跳、稽核與訊息紀錄
- 任務生成：Gripper、Transfer、Working Beam、Infrared 等任務流程產生器
- 自動監控：入出庫、搬運、OCR 驗證、安全狀態、警報、歷史資料清理等 Monitor
- 資料持久化：MyBatis-Plus Mapper 與 XML Mapper 對應 MySQL 資料表
- 報表查詢：警報、綁帶、TT Record、OCR 人工判定等資料查詢

## 環境需求

- JDK 17
- MySQL 8.x 或相容版本
- Maven，或直接使用專案內的 Maven Wrapper
- MQTT Broker，預設為 `tcp://localhost:1883`
- 若需相機功能，需安裝 Hikrobot MVS 相關 Runtime / SDK
- 若需 PLC、序列埠、貼標機、OCR Vendor、ZIP Stocker 等功能，需確認對應設備網路可連線

## 初始化

### 1. 安裝本地相機 JAR

專案依賴 `local.hikrobot:mvs-camera-wrapper:1.0`，JAR 放在 `libs/MvCameraControlWrapper.jar`。若本機 Maven repository 尚未安裝，先執行：

```powershell
.\mvnw.cmd install:install-file `
  -Dfile=libs/MvCameraControlWrapper.jar `
  -DgroupId=local.hikrobot `
  -DartifactId=mvs-camera-wrapper `
  -Dversion=1.0 `
  -Dpackaging=jar
```

### 2. 建立資料庫

預設資料庫連線設定在 `src/main/resources/datasource.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rdf887_01?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Taipei
    username: root
    password: Zz123456
```

請先建立資料庫並匯入初始化 SQL：

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS rdf887_01 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p rdf887_01 < rdf887_01.sql
```

若部署環境不同，請調整 `datasource.yml` 的 host、port、帳號與密碼。

### 3. 檢查外部服務設定

常用設定檔位置：

- `src/main/resources/application.yml`：服務 port、外部 HTTP 服務、貼標機、OCR Vendor、EMQX 管理 API
- `src/main/resources/mqtt.yml`：MQTT Broker、clientId、收送 Topic、心跳與握手機制
- `src/main/resources/camera.yml`：相機設定
- `src/main/resources/serial.yml`：序列埠設定
- `src/main/resources/plc-config/*.yml`：PLC 設備與點位設定

正式部署前請確認設定檔內的 IP、port、帳號密碼與設備名稱符合現場環境。

## 建置與執行

### Windows

```powershell
.\mvnw.cmd clean package
java -jar target\RDF887-01-0.0.1-SNAPSHOT.jar
```

### Linux / macOS

```bash
./mvnw clean package
java -jar target/RDF887-01-0.0.1-SNAPSHOT.jar
```

服務預設啟動於：

```text
http://localhost:8080
```

## 開發常用命令

```powershell
# 編譯
.\mvnw.cmd compile

# 執行測試
.\mvnw.cmd test

# 打包
.\mvnw.cmd clean package

# 直接用 Spring Boot 啟動
.\mvnw.cmd spring-boot:run
```

## 部署批次檔

專案內提供數個 Windows 批次檔：

- `Rdf88701-Service - now.bat`：執行 `target` 內的 JAR
- `Rdf88701-Service.bat`：執行 `target-cim` 內的 JAR
- `target_copy_to_cim.bat`：將 `target` 內容複製到 `target-cim`
- `target_copy_to_test.bat`：將 `target` 內容複製到測試目標資料夾

使用前請確認批次檔內的絕對路徑符合實際部署位置。

## API 與模組入口

主要 REST Controller 位於：

```text
src/main/java/com/czkuo/rdf88701/presentation/web/controller/
```

常見模組包括：

- `AuthController`：登入與認證
- `ContainerController` / `ContainerMainController`：料盒與主檔資料
- `CraneController` / `CraneRequestController`：天車設備與請求
- `GripperController` / `GripperTaskController`：夾爪設備與任務
- `TransferController`：移載設備
- `WorkingBeamController`：工作樑設備
- `Location*Controller`：庫位、流程、帳務與查詢
- `MqttCommandApiController`：MQTT 命令介面
- `Ocr*Controller`：OCR 命令、驗證與人工紀錄
- `RobotTask*Controller`：R007 / R008 / R029 / R031 等自動流程任務
- `AlarmReportController` / `StrappingReportController` / `TtReportController`：報表查詢
- `ZipStocker*Controller`：ZIP Stocker API 與命令介面

WebSocket 相關程式位於：

```text
src/main/java/com/czkuo/rdf88701/presentation/websocket/
```

## 專案文件

詳細文件索引位於 [doc/README.md](doc/README.md)。

- 架構與程式分層：[程式結構地圖](doc/architecture/PROGRAM_STRUCTURE_MAP.md)
- API 規格：`doc/api/`
- 資料存取規格：`doc/data-access/`
- 流程規格：`doc/flow/`
- 外部整合規格：`doc/integration/`
- 維運規格：`doc/operation/`

修改任務生成器、自動流程、API 或資料存取前，建議先對照 `doc/README.md` 中的對應文件與相關程式碼。

## 注意事項

- 設定檔目前包含資料庫密碼、EMQX app secret 等敏感資訊，正式環境建議改以環境變數、外部設定檔或部署平台 Secret 管理。
- PLC、MQTT、相機與序列埠功能會依現場設備狀態影響啟動或執行結果；本機開發時可先確認相關 Monitor 是否需要停用或改用測試設定。
- `target/` 為建置產物，不應作為原始碼修改來源。
- 若異動 Mapper XML、資料表欄位或 Entity，請同步確認 `rdf887_01.sql`、Mapper interface、Service 與前端 API 使用處。
