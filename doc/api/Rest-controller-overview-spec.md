# REST Controller 總覽規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`presentation/web/controller`

## 目的

本文件整理 REST Controller 的職責分組，作為 API 文件補齊前的導覽。詳細 API 可再依模組拆分到 `doc/api/`。

## Controller 分組

| 分組 | Controller |
| --- | --- |
| 認證與權限 | `AuthController` |
| 容器與庫位 | `ContainerController`、`ContainerMainController`、`Location*Controller` |
| 搬運設備 | `CraneController`、`GripperController`、`TransferController`、`WorkingBeamController` |
| 任務與請求 | `CraneRequestController`、`ExternalCraneRequestController`、`GripperTaskController`、`RobotTask*Controller` |
| OCR | `OcrCommandApiController`、`OcrManualLogController`、`OcrVerificationController`、`OcrWebApiController` |
| 外部整合 | `MqttCommandApiController`、`ZipStockerApiController`、`ZipStockerCommandApiController` |
| 相機 | `CameraController`、`HikCameraController` |
| 報表 | `AlarmReportController`、`StrappingReportController`、`TtReportController`、`AlarmActionLogController` |
| 其他 | `HmiDisplayTaskController`、`LogsController` |

## 維護規則

1. Controller 只處理 HTTP request / response、基本 validation 與呼叫 service。
2. 複雜流程應放在 application service 或 domain service。
3. 新增 Controller 時，需同步在本文件與 `README.md` 或相關 API 文件補入口說明。
