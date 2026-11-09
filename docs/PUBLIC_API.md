> Phase4G：動態字典及有界分頁／下一作品已加入，詳細契約與限制見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。

> Phase4C：新增GET /api/public/media/{uuid}，僅服務published引用的managed PNG/JPEG、draft/退役404、PG/檔案故障503、no-store/nosniff，見[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)。

> Phase4B：管理員可發布／下架作品，公開GET立刻依published篩選；未知或草稿皆PROJECT_NOT_FOUND404。其他查詢、405與503契約不變，管理CRUD另見[ADMIN_PROJECTS](ADMIN_PROJECTS.md)。

> Phase4A：本文件公開查詢契約保持不變（匿名GET、公開寫入405、PG503）；管理登入與角色權限另見[ADMIN_AUTH](ADMIN_AUTH.md)，不把管理Cookie或身份混入公開回應。

# 公開作品查詢 API（Phase 3 PG保存）

Java21／Spring Boot3.5.16；預設127.0.0.1:19186。資料來自專屬PostgreSQL，Flyway V1/V2建表與首次seed。改DB資料後查詢即可讀到，重啟不覆寫；沒有登入、寫入endpoint或其他作品的業務服務依賴。[模型與啟動](PERSISTENCE.md)。

## 查詢契約

| GET 路徑 | 結果 |
| --- | --- |
| `/api/public/projects` | `{items,total,catalogTotal,page,pageSize,totalPages}`；items為卡片摘要，不帶長description／evidenceLinks |
| `/api/public/projects/{slug}` | 完整作品，包括範圍、歷史驗證、限制及固定版本來源 |
| `/api/public/projects/{slug}/next` | `{next:summary或null}`，全體published下一筆、末筆回首、單筆null、未知／draft404 |
| `/api/public/categories` | 從有published作品的分類按category sort_order／label讀出陣列 |
| `/api/public/health` | `{status:"UP",catalog:"POSTGRESQL"}`；PG SELECT1成功，不代表完整營運健康 |

列表可組合：`q`搜尋名稱／副標題／摘要／技術，trim且不區分大小寫，最多120個Java UTF-16字元；prepared參數與strpos為literal，不把%/_作wildcard。`category`精確值最多40字元、空白全部；`featured`只能空值、true/false。只回published作品，按project sort_order／slug；total為符合筆數、catalogTotal為公開總數。列表分類／內容／總量在同一PG唯讀repeatable-read snapshot，使用LIMIT/OFFSET有界分頁，page預設1／最大100000、pageSize預設12／最大50；totalPages為ceil(total/pageSize)。跨請求沒有snapshot／cursor保證。

已登錄但未引用的分類200空集合；未知分類／非法featured或分頁／過長輸入HTTP400 `{code:"INVALID_QUERY",message:...}`；搜尋無結果HTTP200且items空，不是錯誤。未知或暫緩slug HTTP404 `{code:"PROJECT_NOT_FOUND",message:...}`。POST/PUT/PATCH/DELETE不提供、HTTP405。成功與領域錯誤回no-store；一般框架錯誤不回stacktrace。

PG不可用回HTTP503 `{code:"CATALOG_UNAVAILABLE",message:...}`，沒有舊JSON或空清單fallback。API使用SELECT-only reader，Flyway帳號分離；GitHub證據私人權限不由平台授予。不放credential、個資、有效QR；HTTPS／gateway／正式節流屬後續，不開寬鬆CORS。

## Vue 行為

所有請求為同源`/api/public`，不傳身份cookie。Vite dev/preview把/api代理到自己的loopback後端；production靜態檔不含API主機地址，正式部署需由同源gateway代理。

首頁GET featured=true，列表讀搜尋／分類與categories，詳情GET slug及獨立next endpoint供下一個作品。無靜態fallback。讀取失敗顯示重試，不當空資料或404；只有明確PROJECT_NOT_FOUND顯示專案404。不完整成功回應拒絕。新條件取消舊請求並以序號核對，切頁不發布舊回應；8秒請求上限。

瀏覽器用package JAR／production dist／真PG，503／不完整／延遲UI情境仍明確route stub。實際PG stop／same-volume start的503及資料恢復由獨立runtime驗收，不能混成相同證據。
