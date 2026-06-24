# RDF887-01 文件索引

本目錄保存專案規格與維護文件。根目錄 `README.md` 是專案入口與啟動說明；本文件是詳細規格索引。

## 分層目錄

```text
doc/
├── architecture/        # 架構、程式結構、資料流
├── api/                 # REST API、WebSocket、MQTT
├── data-access/         # Repository、Mapper、SQL、資料表關係
├── flow/                # R007/R008/R029/R031、搬運流程、Request Generator
├── integration/         # PLC、OCR、ZIP、Camera、Serial、外部系統
└── operation/           # 排程、Monitor、部署、維運、告警
```

## 架構文件

- [程式結構地圖](architecture/PROGRAM_STRUCTURE_MAP.md)

## API 文件

- [MQTT 訊息處理規格](api/Mqtt-message-processing-spec.md)
- [WebSocket 即時推送規格](api/WebSocket-push-spec.md)
- [容器與庫位 REST API 規格](api/Rest-container-location-api-spec.md)
- [REST Controller 總覽規格](api/Rest-controller-overview-spec.md)
- [Auth 與權限規格](api/Auth-permission-spec.md)
- [報表、Safety、Strapping、TT 規格](api/Report-safety-strapping-tt-spec.md)

## 資料存取文件

- [Repository 與 Mapper 資料存取規格](data-access/RepositoryMapper-data-access-spec.md)

## 外部整合文件

- [PLC Adapter 整合規格](integration/PLC-adapter-spec.md)
- [PLC Memory Layout 解析規格](integration/PLC-memory-layout-spec.md)
- [OCR 整合規格](integration/OCR-integration-spec.md)
- [ZIP Stocker 整合規格](integration/ZIP-stocker-integration-spec.md)
- [Camera 與 Serial 整合規格](integration/Camera-serial-integration-spec.md)

## 維運文件

- [Monitor 與排程執行規格](operation/Monitor-scheduling-spec.md)
- [部署與維運規格](operation/Deployment-maintenance-spec.md)

## 流程文件

- [GripperRequest 流程規格](flow/GripperRequest-flow-spec.md)
- [TransferRequest 流程規格](flow/TransferRequest-flow-spec.md)
- [WorkingBeamRequest 流程規格](flow/WorkingBeamRequest-flow-spec.md)
- [IR1RequestGenerator 流程規格](flow/IR1RequestGenerator-flow-spec.md)
- [R007 流程規格](flow/R007流程規格.md)
- [R008 流程規格](flow/R008流程規格.md)
- [R029 流程規格](flow/R029流程規格.md)
- [R031 流程規格](flow/R031流程規格.md)

## 待補文件方向

- 已完成主要架構、API、資料存取、流程、整合與維運文件。後續若新增模組，請同步補入對應分類。
