# 容器與庫位 REST API 規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`presentation/web/controller`、`presentation/web/dto`、容器與庫位查詢入口

## 目的

本文件說明 RDF887-01 專案中容器主資料、容器內容資料、庫位與容器位置追蹤的 REST API 分工。這些 API 主要提供前端查詢、建立、更新與刪除容器資料，並作為庫位畫面、帳務操作與現場狀態檢視的 HTTP 入口。

## 主要 Controller

| Controller | Base path | 職責 |
| --- | --- | --- |
| `ContainerController` | `/api/containers` | 容器 CRUD、依 carrierId/id 查詢、主資料與最新 data 聚合回傳、托盤厚度 attr 維護。 |
| `ContainerMainController` | `/api/v1/container` | 容器主資料分頁查詢，走 application query service。 |
| `LocationPointController` | `/api/location-points` | 庫位主檔分頁查詢，回傳 `LocationPointVO`。 |
| `LocationTrackingController` | `/api/location-trackings` | 容器目前位置追蹤分頁查詢，回傳 `LocationTrackingVO`。 |

## Container API

`ContainerController` 是容器資料的主要維護入口。

| Method | Path | 用途 |
| --- | --- | --- |
| `GET` | `/api/containers` | 分頁查詢目前在 `location_tracking` 內的容器，支援 `query`、`page`、`size`。 |
| `GET` | `/api/containers/{carrierId}` | 依 `carrierId` / `alias_code` 查詢單筆容器與最新 data。 |
| `GET` | `/api/containers/id/{id}` | 依 DB id 查詢單筆容器與最新 data。 |
| `POST` | `/api/containers` | 建立容器主資料，可同時帶入一筆 data 與托盤厚度。 |
| `PUT` | `/api/containers/{carrierId}` | 更新容器主資料、data 與托盤厚度。 |
| `DELETE` | `/api/containers/{carrierId}` | 刪除容器主資料前，先刪除 data 與 attr。 |

## 容器 DTO

| DTO | 用途 |
| --- | --- |
| `CreateContainerRequest` | 建立容器請求。`carrierId` 與 `containerType` 必填，可選填內容資料與托盤厚度。 |
| `UpdateContainerRequest` | 更新容器請求。欄位為局部更新語意，`null` 表示不異動。 |
| `ContainerDataRequest` | 容器內容資料請求，包含數量、層數、OCR 文字與內容種類。 |
| `ContainerMainDto` | 前端容器回應模型，聚合主資料、最新 data 與托盤厚度。 |

## 庫位查詢 API

| Method | Path | 用途 |
| --- | --- | --- |
| `GET` | `/api/location-points/page` | 分頁查詢庫位主檔與狀態。 |
| `GET` | `/api/location-trackings/page` | 分頁查詢容器目前位置追蹤資料。 |

庫位查詢 Controller 不直接組 SQL，會將 query object 交給 application query service，再由 repository / mapper 取得資料。

## 資料流

```text
Frontend
  -> REST Controller
  -> application query service 或 domain repository
  -> infra repository / mapper
  -> MyBatis XML / MyBatis-Plus
  -> MySQL
```

`ContainerController` 因為同時處理主檔、data 與 attr，目前會直接協調多個 repository。一般查詢型 Controller 則以 application query service 為主要入口。

## 維護規則

1. Controller 只處理 HTTP request/response、基本驗證、交易邊界與呼叫應用服務。
2. 複雜查詢與業務判斷應放在 application service 或 domain service。
3. DTO 欄位以 API 語意命名，例如 `carrierId` 對應資料庫的 `alias_code`。
4. 建立/更新 request 應使用 validation annotation 表達基本限制。
5. 更新類 request 中，`null` 優先代表不異動；若要表達刪除或清空，需在 Controller 或 service 中明確定義規則。
6. 修改 API DTO 或 Controller 後，至少執行 `.\mvnw.cmd -DskipTests compile`。
