> Phase4G：分類／技術治理與公開／管理分頁已完成；管理列表改為envelope、分類DML與技術UPDATE/DELETE已授權writer，完整新契約見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。下方交易和作品內容契約保留；舊「不能改分類」權限敘述已由V9擴充。

> Phase4C：圖片上傳／metadata／回收已加入獨立頁，見[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)。下方「圖片上傳另做」為Phase4B原範圍；核心content仍保留media，由新API另改。

# 作品管理（Phase 4B）

公開作品頁保持匿名 GET；管理登入／CSRF／Session 見 [ADMIN_AUTH](ADMIN_AUTH.md)。管理員可新增、修改、發布／下架、設定排序及刪除作品。VIEWER 可檢視所有作品（含草稿）及稽核，不能寫入。圖片上傳、帳號治理及正式部署另做後續里程碑。

## 操作介面

從 `/admin` 進入「作品管理」。新增預設草稿、非精選、自動排在最後；「開發狀態」與「發布」獨立，In Progress 也可發布。編輯文字、技術、功能、後續目標、驗證、限制、挑戰、架構與 HTTPS 證據連結。發布後公開列表、分類、搜尋及詳情立即讀 PG；下架後列表隱藏、詳情404。

- 網址代號建立後固定；`new` 保留給新增頁。刪除後代號仍保留於稽核，不允許重用，避免刪除／重建後舊版本操作套到新作品。
- 排序位置為獨占非負整數。新增留空自動放最後；更新留空保留原值。占用位置409，須選擇空位；尚無拖曳／批次交換排序。自動位置耗盡也409，不溢位成503。
- 每行一項的欄位儲存時 trim／移除空行。技術可使用已有或新增標籤，最多30項且不可重複；共享技術字典不隨作品刪除。
- 有證據連結時必須提供有效 `YYYY-MM-DD` 查核日期。外連只能 HTTPS，禁止內嵌帳密；文字由 Vue escaping 呈現，不接受 HTML 渲染或圖片路徑寫入。
- 既有 cover／screenshots／localDemo metadata 在內容修改時保留，不能透過此 API 更新。刪除需第二次明確確認，移除作品與關聯，保留稽核；不刪除 public 檔案／儲存物件。
- 409 保留目前輸入，使用者核對／複製後選「載入最新版本」才取代表單。不自動重送修改；連線逾時顯示結果未確認，重新讀 PG 核對，避免將未收到回覆當成未保存。

## API 契約

所有管理回應 no-store。服務端身份授權，寫入需目前 Session 的 `X-CSRF-TOKEN`。不接受 client actor／role。未知 JSON 欄位或無法解析的型別400，不靜默忽略未知欄位；可解析的 scalar 仍依 Jackson coercion，再做欄位語義驗證。

| 方法／路徑 | 角色／回應 |
| --- | --- |
| GET `/api/admin/projects` | ADMIN／VIEWER：包含草稿的摘要與 version、published、sortOrder；回 `{items,total,page,pageSize,totalPages}`，q/category/published/page/pageSize見Phase4G |
| GET `/api/admin/catalog-options` | ADMIN／VIEWER：既有 categories、technologies |
| GET `/api/admin/projects/{slug}` | ADMIN／VIEWER：`{project,version,published,sortOrder}`，未知404 |
| POST `/api/admin/projects` | ADMIN：201，建立 version1 |
| PUT `/api/admin/projects/{slug}` | ADMIN：200，整份可編輯內容取代，version+1 |
| DELETE `/api/admin/projects/{slug}` | ADMIN：204，body `{expectedVersion}` |
| GET `/api/admin/projects/{slug}/audit` | ADMIN／VIEWER：最近50筆，時間／id倒序；刪除後仍可讀 |

POST／PUT body：

```json
{
  "content": {
    "slug": "sample-work", "title": "範例作品", "subtitle": "",
    "category": "全端應用", "status": "In Progress", "statusNote": "",
    "summary": "摘要", "description": "內容", "year": "2026",
    "role": "Developer", "duration": "", "featured": false,
    "theme": "forest", "technologyLabel": "", "technologies": ["Vue 3"],
    "features": [], "challenges": [], "architecture": []
  },
  "published": false, "sortOrder": null, "expectedVersion": null
}
```

PUT 必須提供目前 `expectedVersion>=1`；POST 只能缺省／null。content 的必要字串均需存在（title／summary／description 非空），featured 必填，technologies／features／challenges／architecture 必須為陣列。其他可選欄位：plannedGoals、verification、limitations、architectureDescription、githubUrl、liveDemoUrl、evidenceLinks、evidenceCheckedAt。完整欄位長度／陣列上限由 `ProjectContent.validate` 定義；其他已發布欄位保留而非接受覆寫。不可用 public GET 的 Project 直接原封提交，因 media／localDemo 欄位不是寫入契約。

錯誤：401 UNAUTHENTICATED、403 FORBIDDEN／CSRF_INVALID、404 PROJECT_NOT_FOUND；400 INVALID_PROJECT／INVALID_VERSION／INVALID_CATEGORY／IMMUTABLE_SLUG／INVALID_ORDER；409 VERSION_CONFLICT／SLUG_EXISTS／ORDER_CONFLICT／CATALOG_CONFLICT。DB寫入不可用503 CATALOG_UNAVAILABLE；Session／身份依賴不可用仍503 AUTH_UNAVAILABLE。公開寫入仍405。

## 交易與權限

- 每個寫入使用專屬 `portfolio_writer` datasource 與 transaction manager。先取得此 DB 的管理 advisory transaction lock，重新核對 actor enabled／ADMIN／version，再對既有作品 row lock 與 expectedVersion 比對；低流量後台以序列化方式配置排序，未宣稱高吞吐寫入。
- 作品、技術／證據關聯與完整前後快照的 audit INSERT 同一交易。任何一步失敗全部 rollback，包含共享技術新增與刪除時的 child rows。版本衝突沒有新稽核。稽核 actor 取自驗證過的 Session principal。
- V4新增 project_audit，不使用依附作品的FK，所以刪除後紀錄保留。writer 只能 SELECT／INSERT audit，不能 UPDATE／DELETE；public reader／auth沒有audit權限。這是 runtime 的append-only限制，不是防DB owner竄改的不可變儲存／外部簽章。
- writer 僅 SELECT類別／domain表，作品與文字關聯DML、共享technology INSERT、project_media DELETE（清關聯）、audit SELECT/INSERT；身份只准 SELECT(username,role,enabled,version)，不能讀password hash、Session、改帳號／分類／schema。auth仍不能寫作品，public reader仍唯讀。
- V1／V2／V3已發布bytes不變。V4僅為仍符合Phase4A原seed/version的平台介紹加本輪說明，已編輯資料與其他四作品保持。

## 既有 scope 升級

新 scope `init/up` 自動提供獨立 writer role／private env，沒有自動建管理員或重設任何帳密。已有Phase4A scope需在含V4的JAR啟動前：

```bash
python scripts/portfolio_db.py enable-writer --directory work/<原有scope>
```

要求自己的PG正在運行；若原container已停止先start。若已down移除container，先up會啟動保留volume的PG，並在缺writer設定時明確拒絕後续role檢查；執行enable-writer後再up即可。舊Phase3還須先enable-auth，兩項分開、不reset。已有writer role但設定缺失會拒絕，需找回匹配的private env，不重設密碼。正式DB先自行配置同名非owner角色／CONNECT與私有credential，再跑migration，不使用helper接管正式資料。

## 後续

圖片上傳／素材引用與孤兒物件治理、分類／技術治理、分頁／完整稽核導出、拖曳排序；登入限流／帳號治理／HTTPS／獨立migration／備份還原。正式對外前的安全及持久備份驗收仍未完成。稽核含管理身份與草稿，不公開原始trace／有效Cookie／個人資料；測試只用本平台隔離DB與合成內容。
