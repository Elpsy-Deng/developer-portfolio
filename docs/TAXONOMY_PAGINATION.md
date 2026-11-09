# 分類／技術治理與分頁（Phase 4G）

從 `/admin` 進入「分類與技術管理」。ADMIN 可新增、改名、調整分類排序及刪除未引用項目；VIEWER 可讀字典及最近稽核。公開分類只列有 published 作品者，管理選項包含全部登錄分類；前端不再限制固定三個名稱。

## 字典契約

所有回應 no-store；身份、CSRF 與 Session 規則沿用 [ADMIN_AUTH](ADMIN_AUTH.md)。`kind` 僅 `categories`／`technologies`，ID 是服務端產生的 UUID。

| 方法／路徑 | 回應與權限 |
| --- | --- |
| GET `/api/admin/taxonomy/{kind}` | ADMIN／VIEWER；Term 陣列 |
| POST `/api/admin/taxonomy/{kind}` | ADMIN；201 Term，version1 |
| PUT `/api/admin/taxonomy/{kind}/{id}` | ADMIN；200 Term，修改時 version+1 |
| DELETE `/api/admin/taxonomy/{kind}/{id}` | ADMIN；204 空 body |
| GET `/api/admin/taxonomy/audit` | ADMIN／VIEWER；最近50筆，createdAt／id倒序 |

Term 是 `{id,label,version,sortOrder,references}`。分類排序為非負 int 且位置獨占；技術 sortOrder 為 null，按 name／id 排序。references 包含草稿與公開作品。分類名稱 trim 後1–40、技術1–80 Java UTF-16字元，禁止 NUL；分類保留「全部」。名稱唯一、精確比較，沒有 case folding 同義合併。每種字典最多500項，完整選項列表受此上限約束。

分類 POST 精確 body `{label,sortOrder}`，PUT 加 `expectedVersion`；技術 POST 僅 `{label}`，PUT 加 `expectedVersion`；DELETE 僅 `{expectedVersion}`。整數必須是真 JSON 整數且範圍合法，不接受字串、浮點、額外欄位／actor。寫入需目前 Session CSRF header，不由 client 指定身份。

- stale version →409 `TERM_VERSION_CONFLICT`；相同值 no-op 不增加版本／稽核。
- 名稱重複→409 `TERM_EXISTS`；排序重複→409 `TERM_ORDER_CONFLICT`。
- 有引用→409 `TERM_IN_USE`；FK另保護直接DB刪除。含草稿，不能只移除公開引用後就刪除。
- 每次改名最多100個引用作品，超過→409 `TERM_RENAME_LIMIT`，沒有部分變更。字典容量→409 `TERM_LIMIT`。
- 欄位／kind不合法→400 `INVALID_TERM`；不存在ID→404 `TERM_NOT_FOUND`；DB／audit故障→503 `CATALOG_UNAVAILABLE`；身份依賴故障→503 `AUTH_UNAVAILABLE`。

## 版本、交易與稽核

與作品 CRUD 共用此資料庫的 advisory transaction lock；交易內重新核對 actor enabled／ADMIN／version。改名先鎖引用作品並讀完整 before，修改字典、所有引用作品 version+1／updated_at、每份作品 UPDATE snapshot 與一份 taxonomy UPDATE 同交易提交；任一 audit INSERT 故障全部 rollback。分類只有排序更改不改作品內容，因此不提高作品版本。作品的舊編輯頁在改名後收到409，不會用舊分類／技術值覆蓋新字典。

作品編輯原本可新增技術標籤的行為保留；成功新增字典時另 INSERT taxonomy CREATE，同作品／關聯交易，亦受500項上限。該 CREATE 的 references=0 表示關聯插入前的快照；GET Term 顯示現在的引用數。作品刪除不自動刪字典，須在管理頁確認未引用後另刪。字典刪除仍保留 UUID／before／actor／action 的 audit，不用依附字典的FK；相同 label 日後可用新UUID建立，不承接舊expectedVersion。

V9只擴 writer 的分類DML、技術UPDATE/DELETE與 taxonomy_audit SELECT/INSERT；公共 reader 仍唯讀，auth／identity／credentials 不能讀新audit，writer 不能讀password hash／Session或改刪audit／schema。append-only是runtime授權，非防owner竄改／外部簽章。歷史V1–V8 bytes不改；平台介紹僅更新未編輯 Phase4F/version7 seed，四來源作品與既有帳密／Session保持。

## 分頁契約

公開 GET `/api/public/projects` 與管理 GET `/api/admin/projects` 都接受 `page`（預設1、1–100000）／`pageSize`（預設12、1–50）。只能十進位正整數，不接受前導零／負數／浮點／超界；非法查詢400 `INVALID_QUERY`。Spring 空參數使用預設值。

公開回應 `{items,total,catalogTotal,page,pageSize,totalPages}`，管理回應 `{items,total,page,pageSize,totalPages}`；**管理列表已由舊陣列改為 envelope**，client必須一起升級。total 是全部符合篩選的筆數，非本頁筆數；totalPages=ceil(total/pageSize)，空集合0頁。越過最後頁回200、items空但保留total，不自行夾回末頁。每頁按 sort_order／slug、LIMIT/OFFSET 查詢；count與items在同一 request 的 repeatable-read transaction。不同頁請求間不是同一snapshot，若同時新增／排序／刪除可有位移；沒有cursor／跨頁快照保證，也未宣稱大型目錄效能。

公開 q 搜尋名稱／副標題／摘要／技術，管理另搜尋 slug；trim、lowercase、prepared strpos literal，`%`／`_`不當wildcard，最多120字元。category 最多40、精確比較；公開 category 必須已登錄（未引用／只有草稿則200空集合），未知400；管理未知分類則空集合。公開 featured、管理 published 僅空／true／false。分類、狀態、搜尋、pageSize改變後UI重設page1。

公開列表將搜尋／分類／頁碼／每頁數寫URL，可深連結及reload；管理列表篩選不保存URL。兩者取消舊請求並用序號拒絕慢回應。成功回應必須有一致分頁metadata及本頁筆數，否則顯示格式錯誤。頁碼越界空態有「第一頁」回復操作。

詳情的下一作品用 GET `/api/public/projects/{slug}/next` → `{next:summary|null}`，按全體published排序找下一筆、末筆回第一筆，不受目前列表分頁限制、不曝露草稿；只有一個公開作品則null。未知／draft slug404。首頁精選展示至多預設12筆，更多作品從完整列表瀏覽。

## UI、限制與續作

409保留名稱／排序／作品輸入，明確「載入最新項目版本」後才取代；DELETE需勾選確認，服務端仍核對引用。503保留非秘密輸入，手動重新讀取核對；逾時結果未確認，沒有自動重送。保存成功但reload失敗分別提示，不能誤稱保存失敗。若字典被owner外部直接改動，版本／稽核傳播須自行負責；所有正常管理操作應走API。

後續：批次／拖曳排序、完整audit導出／保留／容量治理、真正免費可交付的忘記密碼方案；formal HTTPS／可信proxy／協調PG＋圖片backup/restore。正式部署仍未驗收，Vite preview不當公開服務。驗證結果見 [VERIFICATION](VERIFICATION.md) 與 [HANDOFF](HANDOFF.md)。
