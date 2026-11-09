# Phase5C：可審閱的展示內容版本

`content/showcase-2026-10-13/release.json` 保存五個作品的工程案例、固定來源證據、架構說明及平台Docker／備份／CI現況；`architecture/*.dot` 與PNG是依原始碼整理的架構說明圖，**不是來源應用操作截圖**。圖片可點選查看原尺寸。其他作品數字仍是固定版本的歷史驗證，不在這個平台重算。

作品內容仍完全由PG API提供。bundle不是前端fallback、啟動自動seed或migration；V1–V9、15表與現有備份契約保持。新部署原始seed是歷史快照，需在建立ADMIN後明確匯入這個展示版本。既有部署也先審閱計畫，不默默覆寫編輯內容。

## 規劃、審閱、套用

先依[DEPLOYMENT](DEPLOYMENT.md)建立自己scope、migration與ADMIN，保持自己的gateway/API運行。使用同scope的private帳密檔：

```bash
python scripts/showcase_release.py plan --directory work/deploy-demo \
  --credentials work/deploy-demo/account-portfolio_admin.env \
  --plan work/deploy-demo/showcase-plan.json
```

plan僅讀catalog，保存before/after、每一項expectedVersion、完整before指紋、發布／排序、將新增的架構圖及release SHA。這個檔案可能含草稿，必須保留在private work，不提交／公開。**先查看並審閱所有before/after與media**；命令會輸出plan SHA，將確認過的SHA傳入：

```bash
python scripts/showcase_release.py apply --directory work/deploy-demo \
  --credentials work/deploy-demo/account-portfolio_admin.env \
  --plan work/deploy-demo/showcase-plan.json --plan-sha256 <已審閱計畫的SHA256>
```

兩動作都是ADMIN-only，驗證本repo私有scope／非root UID、自己的PG、運行中的API/gateway及精確loopback port；不是對任意URL送密碼。共用deploy scope lock，不能與同scope部署命令競爭。apply先核對全部targets的snapshot/version與固定release/media SHA，再用既有CSRF／expectedVersion的作品PUT与SCREENSHOT upload。

只改宣告的狀態說明、平台階段標籤、實作／規劃／驗證／限制、工程案例、架構說明與證據連結／查核日；現有title、角色字段、description、分類、技術、featured、狀態、發布／排序及原有cover/screenshots保持。這些既有字段若需要更新，仍由管理頁明確編輯。圖片作SCREENSHOT append，不取代封面；介面依標題prefix「架構說明圖」放入架構區，其他圖片留原畫面區。

每個作品／每次媒體上傳使用既有交易與snapshot audit；**跨五個作品不是一筆原子交易**。`<plan>-result.json`先寫IN_PROGRESS，保存已完成slug/version与目前項目；中途故障標PARTIAL_OR_UNCONFIRMED，連線中斷／HTTP回應遺失不自動重送。先讀管理頁／audit與journal核對，再產生新計畫。不要用owner SQL、修改migration/history或重新產生計畫盲覆蓋。

版本、文字、media、publish/order在plan後變動，都拒絕套用；已嘗試的plan不可重放。新plan若文字與已匯入圖片metadata相同，做no-op不增加version/audit或重複圖片。metadata用於辨識同一已匯入圖，不是對既有任意圖片的pixel/hash來源鑑定；原source PNG SHA保護此次上傳材料，人工改圖仍要核對圖與audit。

## 來源與個人資料

固定來源全部透過GitHub commit／README／HANDOFF與相關程式唯讀核對，六個來源repo＋平台的full SHA在release sources中。發呆亭Booking／Notification、ELPSY preorder與App realm、Encore outbox/Lua/DIRTY、Atlas授權context/RRF/引用與平台secret separation／備份／CI各有三個情境、決策與取捨。

使用者已指定**個人資料暫不公開**。About移除未核實的所在地，工具箱標示為作品程式碼涉及的技術；不編造姓名／聯絡方式／任職／年資／熟練程度。詳情的「專案工作範圍」不當作個人任職證明。release不覆寫role字段。

Atlas保留原本已核實的歷史RAG截圖；發呆亭／ELPSY／Encore固定來源沒有找到可核實的已提交產品截圖，原placeholder仍保留。此階段另保存五張**實際平台展示頁**的390px合成資料截圖：顯示真PG內容與真上傳PNG，並不是這三個來源應用正在運行的畫面；不能以架構圖或平台頁冒充它們的產品截圖。

## 驗證與界線

新增release防護unit tests、真ADMIN／VIEWER、stale全targets preflight、人工role保留、原media/publish/order/audit、重放拒絕、新plan no-op、正常API restart保存与4個desktop/mobile browser；原105後端／27容器／43browser另完整回歸。CI的safe summary將showcase checks/browser單獨project，不uploadplan/journal/raw screenshots或Cookie。

實測結果見[VERIFICATION](VERIFICATION.md)。這次只改展示平台，沒有啟動其他作品服務／模型、沒有對來源repo改碼；公開HTTPS、本機／真機／真provider及來源應用全套合成操作截圖仍各自補驗。
