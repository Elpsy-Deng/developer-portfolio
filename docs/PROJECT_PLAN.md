# Project Plan

## Goal

Build a developer portfolio platform for job applications. The portfolio should present projects clearly and let visitors understand the engineering decisions behind each project.

## Phase 1 - Frontend MVP

Use:
- Vue 3
- Vite
- TypeScript
- Tailwind CSS
- Vue Router

Create:
- Home page
- Projects page
- Project detail page
- About page
- Responsive layout

Use mock project data only.

Do not add yet:
- Spring Boot
- Database
- Authentication
- RBAC
- Admin backend
- Deployment

## Phase 2 - Backend

Add Spring Boot REST APIs and replace mock data with API calls.

2026-10-12：Phase2已加入Java21／Spring Boot公開列表／詳情／分類／搜尋，Vue使用API而非靜態fallback，唯一資料為backend catalog JSON。維持既有版型、深連結及錯誤／空態；下一個里程碑是Phase3真正隔離PostgreSQL／Flyway，不把其他作品DB或帳號合併進平台。

## Phase 3 - Persistence

Add PostgreSQL for project, technology, image, and project-technology relationships.

2026-10-12已加入專屬PG17／Spring JDBC／Flyway V1/V2，作品／分類／技術關聯／媒體／證據持久化、公開reader與migration owner分離，沒有JSON fallback。下一個階段管理身份／角色及作品CRUD，保持migration不可變與API契約。媒體表目前存路徑／說明，不代表上傳與物件管理已完成。

## Phase 4 - Admin

Add project CRUD and media management.

2026-10-12 Phase4A已加入Spring Security、PG Session、CSRF、ADMIN／VIEWER與管理登入／概覽；不包含作品寫入。下一個可獨立驗證里程碑為作品CRUD、樂觀版本與稽核、精確writer DB權限；其後圖片治理。正式公開前另驗登入限流、帳號治理与HTTPS。

2026-10-12 Phase4B已加入作品CRUD、草稿／發布／stored排序、expectedVersion409、同交易snapshot稽核與獨立writer；不改既有media。下一個獨立里程碑圖片上傳／素材治理，其後身份治理與部署安全驗收。

2026-10-12 Phase4C已加入PNG/JPEG驗證重編碼、media版本／audit、draft與public引用讀取、本機adapter及安全回收。SeaweedFS未串接，下一里程碑身份治理／限流與Session絕對期限，再分類／技術／分頁及協調備份／部署。

2026-10-13 Phase4D已加入PG持久／有界／原子登入配額、通用429與前端倒數、8小時絕對Session期限／舊Session重登；不信任轉送標頭。下一個獨立里程碑帳號／角色治理（最後ADMIN、version／撤銷、敏感資料稽核），其後分類／分頁与正式部署／備份。

2026-10-13 Phase4E帳號建立／目錄／角色與啟用治理、expectedVersion、最後啟用ADMIN保護、獨立identity writer及同交易安全audit/Session撤銷已完成。建議下一里程碑本人密碼更改、忘記密碼的重設交付另拆；之後分類／技術與分頁，再部署/協調backup還原。

2026-10-13 Phase4F已實作本人密碼更改／現有密碼驗證、獨立credentials runtime、PG持久配額／CAS／版本／安全PASSWORD_CHANGE／全Session撤銷。忘記密碼的重設交付不在本輪；下一個獨立里程碑分類／技術治理及分頁，再正式部署與協調備份。

2026-10-13 Phase4G已加入動態分類／技術CRUD、version／引用保護／snapshot稽核與改名連帶作品版本；公開／管理有界分頁、URL深連結、全體公開下一作品。完整契約見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。下一步PG＋圖片協調備份／隔離還原；忘記密碼的免費可信交付方案另定，批次排序／audit導出與正式部署另做。

2026-10-13 Phase4H已加入離線協調PG＋圖片備份、全新隔離scope restore、資料／grants／metadata／bytes檢核、Session排除與存取保護。無schema／seed變更；CLI與演練見[BACKUP_RESTORE](BACKUP_RESTORE.md)。下一個獨立里程碑正式app Docker與獨立migration交付，再HTTPS／可信proxy與加密／異地備份等營運验收；不混其他作品DB。

## Phase 5 - Deployment

Containerize services and deploy behind Nginx with HTTPS.

2026-10-13 Phase5A：私人loopback app Docker、獨立一次性migration、reader-only schema validation、同源Nginx與固定image相容app回退已加入。操作與界線見[DEPLOYMENT](DEPLOYMENT.md)。下一個獨立階段建議CI／自動build驗證，公開HTTPS／可信proxy再拆；忘記密碼、備份加密／離機／排程與本機驗收仍待，不把Cloud容器交付當成正式公開部署。

2026-10-13 Phase5B：加入GitHub Actions CI、可本地重現的scoped驗證與安全evidence projection。完整package／frontend build／Docker／真容器browser、清理與負向證據防護見[CI](CI.md)，實際結果見[VERIFICATION](VERIFICATION.md)。下一步展示內容收尾；公開HTTPS／可信proxy獨立驗收，不加入自動部署。

2026-10-13 Phase5C：五作品各三個工程案例、固定SHA來源、架構說明圖與平台現況更新已加入版本化content bundle。透過既有ADMIN API、先審閱計畫／全targets stale preflight／CAS／CSRF／audit保存PG，保留人工字段與既有media；不是前端fallback或silent seed。個人資料依使用者選擇暫不公開。來源產品截圖缺口明確保留，另存真平台頁合成fixture截圖。操作見[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)。下一階段公開HTTPS／可信proxy／peer quota及匿名Session有界節流；不在本階段自動公開或部署。

2026-10-13 Phase5D：實作独立HTTPS scope、canonical Host／轉址／Secure Cookie、明確私有network＋key的gateway peer、PG持久／有界匿名Session admission与CLI可信CA內容匯入。操作見[HTTPS](HTTPS.md)，原V1–V9保持。本地VFS未完成容器回歸；[Hosted標準映像完整驗收](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847)已實跑115／27／43／17＋5／30真TLS並核對唯一安全摘要。實際public domain/hosting/DNS／CA与外網browser驗收未確認，不自動部署。
