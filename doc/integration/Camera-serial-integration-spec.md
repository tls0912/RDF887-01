# Camera 與 Serial 整合規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`application/service/camera`、`infra/serial`、相機與序列埠設定

## 目的

本文件說明相機抓拍與序列埠事件的整合邊界。相機整合負責 HIKROBOT 裝置列舉、抓拍、存檔與 Base64 回傳；序列埠整合負責多埠開啟、分包、事件發布與自動重連。

## Camera

主要元件：

- `HikCameraSnapService`：使用 Hikrobot MVS wrapper 列舉 GigE/USB 相機，依 index 或 IP 抓拍，支援存檔、覆蓋、曝光設定與 Base64。
- `CameraModbusService`：相機相關 Modbus 控制服務。
- `CameraController` / `HikCameraController`：提供測試或 Web API 入口。

維護規則：

1. 相機 SDK/JAR 依賴需維持在 `libs/` 與 Maven local install 流程。
2. 抓拍檔案路徑、格式、品質與 mock 模式需由設定檔控制。
3. 相機服務不應直接處理 Robot 或 ZIP 流程，應由上層 service 編排。

## Serial

主要元件：

- `SerialPortManager`：依 `serial.ports[*]` 開啟多個序列埠，支援 LINE、STX_ETX、FIXED 分包。
- `SerialFrameEvent`：每切出一幀資料後發布事件，供上層監聽。

維護規則：

1. 新增序列埠時，先補設定檔 alias、baud rate、protocol 與 delimiter。
2. 序列埠資料解析應以事件為界，不在底層 manager 混入業務流程。
3. 斷線重連需保留退避，避免現場設備離線時忙等。
