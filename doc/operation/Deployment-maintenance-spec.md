# 部署與維運規格

## 文件資訊

- 建立日期：2026-06-24
- 適用範圍：部署、設定檔、啟動批次、驗證與維運注意事項

## 目的

本文件整理專案部署與維運時需注意的設定、啟動、驗證與風險。根目錄 `README.md` 保留快速啟動說明，本文件補維運角度的規則。

## 主要設定

- `application.yml`：主設定與匯入。
- `datasource.yml`：MySQL 連線。
- `mqtt.yml`：MQTT broker、topic、心跳與握手。
- `camera.yml`：相機與抓拍設定。
- `serial.yml`：序列埠設定。
- `plc-config/*.yml`：PLC 裝置與點位。

## 啟動與驗證

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd clean package
java -jar target\RDF887-01-0.0.1-SNAPSHOT.jar
```

維運前應確認：

1. Java 17 JDK 可用。
2. MySQL、MQTT Broker、PLC、相機、OCR、ZIP 端點設定正確。
3. 本地第三方 JAR 已安裝到 Maven local repository。
4. 正式環境不得把密碼與 secret 直接提交到公開 repository。

## 維護規則

1. 修改設定檔後需至少跑 compile 或啟動 smoke test。
2. 修改 PLC/OCR/ZIP/Serial/Camera 整合後，需確認現場設備離線時不會讓服務啟動失敗。
3. `target/` 是建置產物，不作為原始碼修改來源。
