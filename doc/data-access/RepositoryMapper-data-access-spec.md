# Repository 與 Mapper 資料存取規格

## 文件資訊

- 建立日期：2026-06-24
- 依據文件：[程式結構地圖](../architecture/PROGRAM_STRUCTURE_MAP.md)
- 適用範圍：`domain/repository`、`infra/repository/impl`、`infra/mapper`、`src/main/resources/mapper`

## 目的

本文件說明 RDF887-01 專案中資料存取層的責任分工、呼叫流程與註解維護規則。目標是讓新增查詢、調整 SQL、或追查資料流時，可以快速判斷應修改哪一層，並避免把業務規則散落到 Mapper XML。

## 分層責任

| 層級 | 主要目錄 | 責任 |
| --- | --- | --- |
| Domain Repository | `src/main/java/com/czkuo/rdf88701/domain/repository` | 定義應用與領域需要的資料存取能力，不處理 MyBatis 細節。 |
| Infra Repository Impl | `src/main/java/com/czkuo/rdf88701/infra/repository/impl` | 實作 Domain Repository，組合 MyBatis-Plus、Mapper XML、自動補歷史紀錄與必要轉換。 |
| Mapper Interface | `src/main/java/com/czkuo/rdf88701/infra/mapper` | 宣告 MyBatis-Plus 基本 CRUD 與 XML 對應的自訂查詢方法。 |
| Mapper XML | `src/main/resources/mapper` | 保存 SQL、resultMap、共用欄位清單與查詢用途註解。 |
| Entity / DTO | `src/main/java/com/czkuo/rdf88701/infra/entity`、`infra/dto` | 承接資料表欄位或跨表查詢結果。 |

## 標準呼叫流程

```text
Controller / Monitor / Service
  -> application service
  -> domain repository interface
  -> infra repository implementation
  -> infra mapper interface
  -> mapper XML / MyBatis-Plus
  -> MySQL
```

一般 CRUD 優先使用 MyBatis-Plus 的 `BaseMapper`。涉及跨表、最新資料、狀態彙整、倉儲場景查詢、或報表統計時，才放入 Mapper XML。

## 新增資料存取功能規則

1. 先確認呼叫端需要的是領域能力，還是純資料查詢。
2. 若上層應依賴抽象，先在 `domain/repository` 增加方法。
3. 在 `infra/repository/impl` 實作方法，集中處理空值、分頁、歷史紀錄、狀態判斷與資料轉換。
4. 若 SQL 超出單表 CRUD，於 `infra/mapper` 宣告方法，並在 `src/main/resources/mapper` 補 XML。
5. Mapper XML 每個 `select`、`insert`、`update`、`delete`、`resultMap`、`sql` 區塊前方需有 SQL 用途註解。

## Mapper XML 註解規則

- 註解要說明「查什麼、更新什麼、結果映射什麼」，不要只重複方法名稱。
- 可以保留方法 id，但中文用途必須優先清楚。
- 若 SQL 有特殊條件，例如只取最新資料、排除已完成任務、限制倉儲儲位，必須在註解或 SQL 旁補明。
- XML 檔案需維持狀態標註：`2026-06-24 ver XML 狀態：已檢查` 或 `2026-06-24 ver XML 狀態：已修改`。

## ContainerMain 資料存取範例

`ContainerMainRepository` 是容器主資料的領域資料存取入口。它將「依容器代碼查詢」、「查倉儲內容器」、「查仍在處理中的容器」、「拆併帳務命名」、「容器狀態變更」等能力公開給應用服務。

實作鏈如下：

```text
ContainerMainRepository
  -> ContainerMainRepositoryImpl
  -> ContainerMainMapper
  -> ContainerMainMapper.xml
  -> container_main / container_data / location_tracking / location_point / crane_request / crane_task
```

目前 `ContainerMainMapper.xml` 的重點 SQL 包含：

| 方法 | 用途 |
| --- | --- |
| `selectWithLatestDataById` | 查詢容器主資料，並帶出最新一筆 `container_data`。 |
| `findAllInWarehouse` | 查詢目前位於倉儲儲位的容器主資料。 |
| `findAllInWarehouseWithLocation` | 查詢倉儲內容器，並帶出目前庫位資訊。 |
| `selectProcessingContainerIds` | 查詢仍有未接受請求或未完成任務的容器 ID。 |
| `findMaxSplitIndexByBase` | 查詢拆帳命名尾碼最大值。 |
| `findAllInWarehouseWithLocationByContentKind` | 依最新 `container_data.content_kind` 篩選倉儲內容器。 |
| `updateStateById` | 更新容器狀態與結束時間。 |

## 維護注意事項

- Repository interface 不應直接暴露 MyBatis `Wrapper` 或 XML 細節。
- Repository implementation 可以使用 MyBatis-Plus，但要讓呼叫端只看到明確方法語意。
- Mapper XML 的欄位別名必須與 Entity / DTO 屬性對應。
- 查詢最新資料時，需明確指定最新判斷依據，例如 `MAX(id)` 或時間欄位。
- 修改 SQL 後至少執行 `.\mvnw.cmd -DskipTests compile`，確認 XML 與 Mapper method 可以被載入。
