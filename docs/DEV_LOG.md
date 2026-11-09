> 開發紀錄：原 README 的完整里程碑紀錄（保留歷史，不再更新）。最新說明見 [README](../README.md)。

# Developer Portfolio

Java Full Stack / Backend Engineer 求職作品集。Vue串接Java21／Spring Boot公開API，作品、分類、技術、媒體與證據已保存於專屬PostgreSQL／Flyway。管理登入／角色權限與作品 CRUD／版本／稽核已加入，圖片上傳／安全回收與持久登入限流／Session絕對期限已加入，帳號／角色治理與最後管理員保護已加入，本人密碼更改、動態分類／技術治理及公開／管理分頁亦已加入，協調備份與隔離還原工具、私人同源容器交付／獨立 migration／相容 app 回退已加入，CI自動驗證與可審阅的工程案例／架構圖展示版本已加入，私人HTTPS交付／可信gateway來源與匿名Session配額已實作，忘記密碼重設與公開 HTTPS 依[PROJECT_PLAN](PROJECT_PLAN.md)續作。

每輪先同步遠端 main、讀 [HANDOFF](HANDOFF.md)、核對 Git；里程碑測試/build、更新交接、commit/push main。共同規範見 [PLATFORM_WORKFLOW](PLATFORM_WORKFLOW.md)。

## 目前展示

| 作品 | 現況與邊界 |
| --- | --- |
| 發呆亭 | 預約、現場住宿、角色權限、通知與隔離還原；Cloud 展示收尾，真服務與本機待驗 |
| ELPSY Shop | 現貨、整筆預購、SeaweedFS、員工治理、Flutter App 第一版；交易 mock、真機/iOS 待驗 |
| Encore 售票 | 排隊、MQ、Redis Lua、有界壓測/還原/故障證據；持續預扣受 DIRTY gate 限制 |
| Atlas | 免費真實本地 RAG、混合檢索、引用与權限；品質失敗與本機驗收保留 |
| Developer Portfolio | 公開API／Vue、PG／Flyway、管理登入／角色權限、作品CRUD／版本／稽核、PNG/JPEG圖片管理、持久登入限流與8小時絕對Session期限、帳號角色／最後管理員保護、本人密碼更改、備份／還原、私人同源 Docker／migration 交付；自架主機自動部署，公開唯讀展示經 Tailscale Funnel |

首頁精選前四個作品，列表包含平台共五個。日日選物暫緩、RBAC/Python 占位移出展示，沒有刪除那些作品的遠端程式。

五個作品各有三個工程案例與一張架構說明圖，內容由[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)的ADMIN API計畫／審閱／套用流程保存到PG；新部署須明確匯入，V1–V9初始seed不自動變更。

內容依固定來源版本整理，詳細查核見 [PROJECT_CATALOG](PROJECT_CATALOG.md)。產品整體保留 In Progress；Cloud 展示完成不等於正式營運。測試與品質數字是各作品歷史條件，不是本平台重新驗收。GitHub 倉庫目前為私人。公開唯讀展示：https://swaggyserver.tail5b604b.ts.net:8443/ （Portfolio 與發呆亭／Shop／Encore 唯讀頁；Atlas 不公開），不填 localhost 連結。

Atlas 的兩張真實 RAG 保存截圖來自其第四里程碑；其他封面是裝飾示意。架構圖明確標為說明圖，另有[五張平台頁保存截圖](screenshots/showcase-2026-10-13/README.md)，使用自己隔離PG的合成資料，不能當作來源應用操作截圖。新截圖須使用合成資料，不公開有效 QR、會員個資、Cookie 或秘密。

## 啟動與驗證

需要JDK21、Maven3.9、Node>=22.12、npm與Docker Compose v2。本輪Node24，保留lock。只啟動自己的PG／API／前端，不使用其他作品DB或模型。詳見[PERSISTENCE](PERSISTENCE.md)、[ADMIN_AUTH](ADMIN_AUTH.md)、[ADMIN_PROJECTS](ADMIN_PROJECTS.md)、[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)、[LOGIN_SAFETY](LOGIN_SAFETY.md)、[ADMIN_ACCOUNTS](ADMIN_ACCOUNTS.md)與[SELF_PASSWORD_CHANGE](SELF_PASSWORD_CHANGE.md)。預設登入Cookie需HTTPS；只有loopback HTTP驗收明確設PORTFOLIO_COOKIE_SECURE=false。

```bash
npm ci
python scripts/portfolio_db.py init --directory work/pg-test --port 25486 --database portfolio_test
python scripts/portfolio_db.py up --directory work/pg-test
python scripts/portfolio_db.py exec --directory work/pg-test -- mvn -f backend/pom.xml clean package
python scripts/portfolio_db.py down --directory work/pg-test --purge
python scripts/portfolio_db.py init --directory work/pg-demo --port 25487 --database portfolio_demo
python scripts/portfolio_db.py up --directory work/pg-demo
PORTFOLIO_COOKIE_SECURE=false python scripts/portfolio_db.py exec --directory work/pg-demo -- npm run backend:start
```

另一個終端：

```bash
npm run dev
npm run build
npm run preview
```

API預設127.0.0.1:19186；前端經同源/api代理。PORTFOLIO_PORT可改API埠，前端另以PORTFOLIO_API_URL指定對應loopback origin。不重用其他作品的服務。build包含型別檢查；dist/、backend/target/不提交。[API契約](PUBLIC_API.md)。

瀏覽器回歸使用 production dist 與自己的 loopback preview，不啟動其他作品或模型：

```bash
npm run build
# 需要可用Chromium；此例使用系統/usr/bin/chromium，其他機器指定實際可執行檔：
PORTFOLIO_CHROMIUM_PATH=/usr/bin/chromium python scripts/run_persistence_runtime.py --output work/<全新驗收目錄> --java-home <JDK21目錄>
```

完整runtime工具自建PG，使用已build的package JAR與production preview，並實測API/PG restart、登入／CRUD／稽核／圖片bytes保留、登出與DB停機503；要求25487／19186／15186空閒。只跑browser須提供自己scope的DB與合成ADMIN/VIEWER測試帳號env（見runtime工具）；建議使用完整runtime，不借其他作品服務。browser43的登入429／到期与503／不完整／延遲仍是明確route stub；真DB outage由runtime另驗。全部retries=0，結果留ignored work新目錄，不覆寫失敗證據。[結果](VERIFICATION.md)。

## 資料與頁面

- backend/src/main/resources/db/migration/：不可變V1 schema／V2首次seed，V3身份／JDBC Session、V4作品稽核、V5managed images、V6登入配額、V7帳號治理／安全身份稽核、V8本人改密碼、V9分類／技術治理；查詢來源是PG，沒有JSON fallback。
- backend/src/main/java/dev/elpsy/portfolio/catalog/：Spring JDBC唯讀查詢、摘要／詳情DTO、API、DB guard與503。
- infra/compose.pg.yml、scripts/portfolio_db.py：自己的PG scope、private env與分離帳號。
- src/api/projects.ts／composables/useRemote.ts：同源查詢、格式核對、取消及拒絕舊結果；無靜態fallback。
- src/types/project.ts：共用作品模型。
- src/data/profile.ts：技術工具依作品來源列出；使用者指定個人資料暫不公開，不編造姓名／聯絡方式／所在地／任職／年資。
- src/views/：首頁、列表、詳情、關於我、404、管理登入／概覽／作品編輯／圖片管理／稽核。
- public/projects/：可公開的真實歷史截圖。
- tests/portfolio.spec.ts、playwright.config.ts：production 瀏覽器回歸。

GitHub 外連帶 noopener noreferrer。未知專案/路由呈現404，搜索包含名稱、摘要、技術與副標題。Phase5A Nginx 已處理 HTML5 history deep link 與 missing asset 404；Vite preview 只供本機驗證。

## 接續範圍

Phase4D登入限流與Session絕對期限已實作：預設每300秒帳號30／連線来源60／全站120次，PG配額不因重啟清零；閒置30分鐘或登入滿8小時需重新認證，舊Session缺timestamp也須重登。忽略轉送標頭，正式可信proxy另驗。圖片仍為免費獨立本機adapter、SeaweedFS尚未串接。Phase4E帳號建立／角色與啟用、最後ADMIN保護、同交易撤銷／安全稽核已加入；既有scope升級先enable-identity，API建立初始密碼由管理員用受信任管道交付。Phase4F本人驗現有密碼後更改已加入，持久配額/版本/安全audit/全部Session撤銷；既有V7 scope升級先enable-credentials。Phase4G動態字典、版本／引用保護／稽核、改名傳播與有界分頁已加入，使用[分類／技術與分頁](TAXONOMY_PAGINATION.md)核對。Phase4H協調備份／隔離還原已加入，操作見[BACKUP_RESTORE](BACKUP_RESTORE.md)。Phase5A私人Docker／獨立migration／相容app回退已加入，操作見[DEPLOYMENT](DEPLOYMENT.md)。Phase5B CI操作見[CI](CI.md)。Phase5C展示內容操作見[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)，下一步公開HTTPS／可信proxy；忘記密碼的免費可信交付另定。已發布migration不可改checksum，各作品保持獨立。本機HTTPS、App／模型／真服務補驗收見[CLOUD_LOCAL_FOLLOWUP](CLOUD_LOCAL_FOLLOWUP.md)，現在不要求下載到使用者電腦。

Phase4G驗收：99真PG／HTTP tests、74 packaged runtime、43 production browser全過；migration V1–V8／原12圖bytes保持。證據與限制見[VERIFICATION](VERIFICATION.md)，Phase4H已補PG＋圖片協調備份／隔離還原；忘記密碼的免費可信交付管道另定。

備份工具使用Linux／WSL2標準Python與既有PG17容器client，免費／離線，先停自己的API與維護工作；全新scope restore，不覆寫既有volume。排除Session data，保留帳號hash／audit／quota與圖片bytes；原密碼需重新登入。Bundle含私有內容，只留private ignored work，不是加密／異地備援。完整操作與故障復原見[BACKUP_RESTORE](BACKUP_RESTORE.md)。

Phase4H本輪驗收：7 Python防護／48真PG與HTTP backup-restore；99後端、74 runtime、43 browser重新全過，production build成功。來源隔離及復原後權限／媒體／Session行為均實測，證據見[VERIFICATION](VERIFICATION.md)。

Phase5A容器操作／升級／相容回退見 [DEPLOYMENT](DEPLOYMENT.md)。API不持有owner／bootstrap秘密或執行migration；production dist与API由同源loopback Nginx提供。本輪驗收：105後端 tests、27容器檢查及43瀏覽器全過，production build成功；最終固定JAR/dist與標準app/gateway Dockerfile映像執行；公開HTTPS／可信proxy與使用者本機容量仍待；CI已由Phase5B補齊。

Phase5B [GitHub Actions CI](CI.md)以隔離PG跑完整package／frontend build／Docker與真gateway browser回歸，只上傳安全統計與hash；本地驗證與Hosted執行狀態分開記錄於[HANDOFF](HANDOFF.md)。不包含自動部署。

Phase5B已完成本地及[GitHub Hosted CI首輪](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38351233282)：105後端／27容器／43browser、build與cleanup/upload全過。安全artifact只有summary.json，原始私有資料不公開。

Phase5C本輪本地全流程：18 Python guards、105後端、27容器／43原有browser，新增17展示API／4展示browser全過；固定SQL／public素材與資料治理保持。[GitHub Hosted本版本](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38356320602)亦已成功，實際下載唯一安全摘要核對105／27／43／17／4與cleanup0；詳見[HANDOFF](HANDOFF.md)。

Phase5D操作見[HTTPS](HTTPS.md)：獨立TLS scope、canonical origin、可信peer與PG匿名Session admission；公開網域／部署主機尚未確認。本地初版114後端與build/23guards已過，VFS ENOSPC使本地容器驗收未完成；[本輪GitHub標準映像驗收](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847)已通過115後端／27容器／43原browser／17展示API＋5browser／30真TLS checks，唯一安全artifact實際下載核對，見[HANDOFF](HANDOFF.md)。不把private TLS驗證當已公開網站。
