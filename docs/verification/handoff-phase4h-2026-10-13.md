# 開發交接 — Developer Portfolio

## 2026-10-13：Phase4H PG＋圖片協調備份與隔離還原（本輪最新）

起始remote main `b23baf065bf5cefacc2fe5f8a2051da7fa300d18`；fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者授權上輪建議的backup／restore，只改developer-portfolio。cloud runtime revision367／current／enforced unrestricted、無configured secret／identity；proxy／CA／明確local Docker socket與VFS保持。SDK唯讀借用、cache/raw/private backups留自己的work；無精確usage工具，完成此獨立里程碑先保存，不接正式部署大型階段。

### 本次完成內容

- 免費Python標準庫＋既有PG17 client的offline CLI：backup／verify／restore／recover-access，scope／loopback／標籤／private root核對、檔案lock、NOLOGIN與原旗標journal、拒絕running API／任何其他DB client；不終止client／停別人的服務。PG bootstrap管理通道保持，角色／password不reset。before/after資料／grants／images一致才標COMPLETE，普通fail finally恢復源角色；SIGKILL/PG故障可由匹配journal手動恢復。
- pg_dump custom/public schema＋同窗口local images；保存domain／版本／三種audit／身份hash/role/enabled/version／兩類quota／Flyway，Spring Session／attributes資料故意排除。新scope產生新DB連線密碼，不複製source env／初始password交付檔；使用者hash保持、所有恢復登入要重新認證。
- Bundle private700/600、manifest／dump／圖片SHA及bytes、COMPLETE最後fsync、migration CRC/SHA／15表指紋、符號連結／跳脫／duplicate/extra與尺寸上限。active/引用asset缺檔或bytes不符拒絕，待退役／canonical孤兒檔可複製、mtime保持、已缺失退役metadata可重試GC；incoming須空。只接受repo work，無tar解包。
- restore只建立從未存在的scope/directory／新volume／空表；先驗bundle再初始化，runtime/owner先NOLOGIN。保留standard public schema、reviewed TOC只排除CREATE SCHEMA public、清helper reader table default grant再套archive ACL/default ACL；pg_restore owner有效角色、single-transaction/exit-on-error，不用--clean／Flyway repair/baseline/reset。資料／table-column-schema-default-DB grants／role限制／圖片與asset ledger核對後才ready與恢復LOGIN；檢核失敗target隔離、不動source。最後權限恢復回應若失敗須核對狀態，不直接啟動。
- helper新增有界35秒final TCP＋SQL readiness，避免Compose看見init-only temporary Unix socket後太早建role；只重試readiness，不retry角色寫入／功能assertion。無新DB migration／seed／Java／Vue業務修改，V1–V9與12原public assets保持。

### 重要修改檔案

scripts/portfolio_backup.py（guard／offline協調／檔案完整性／新scope restore／access recovery）、scripts/run_backup_restore_drill.py（真PG／HTTP與負向／來源隔離／GC／restart）、scripts/test_portfolio_backup.py（7個artifact防護）、scripts/portfolio_db.py（final TCP/SQL readiness）；AGENTS／README、[BACKUP_RESTORE](BACKUP_RESTORE.md)、PG／身份／計畫／來源／Cloud-local待補／VERIFICATION、[安全JSON](verification/backup-restore-phase4h-2026-10-13.json)及本交接。

### 測試／build結果

- **7 Python防護tests全過**：dump／manifest／seal損毀與private permissions、traversal／duplicate、symlink ancestry/file/outside work、migration/role/session drift、occupied listener／scope lock、active missing／wrong bytes及retired metadata。**Maven clean package 99真PG17.11／real HTTP全過**，failure/error/skip0；本轮重新跑原99，沒有新Java tests／H2/mock／skip。Vue typecheck＋production build通過。
- 新自己的source／restored／quarantined PG，固定JAR：**48 backup/restore checks全過／exit0**。source realADMIN／VIEWER及停用／identity audit、dynamic taxonomy／兩父version4 snapshot、3真PNG（public/draft/retired）＋一old orphan；quota/Session source實存。拒絕running/API client不終止它、missing／symlink image、fail finally原flags恢復、模拟中斷journal recovery；source rows/grants保持。59000-byte custom dump／4 objects（合成小資料，不是吞吐／RTO benchmark）。
- restore既有source／corrupt dump/image在target creation前拒絕；re-sealed invalid archive由真PG client拒絕，新target NOLOGIN／無ready／0 partial tables。成功restore新scope所有資料／audit/hash/quota/history/grants與圖exact matching，新DB連線密碼全換；舊Cookie401、原password ADMIN可重登、disabled帳號401、enabledVIEWER可登入但寫403、public圖byte相同／draft404、ref409。writer保存version5/audit，正常ADMIN GC回收1 retired＋1old orphan；不改source DB或4張source圖。same-volume PG/JAR restart保持結果，新Session仍可用／舊Cookie仍401，source恢復API時原Session可用。
- **74 packaged runtime＋43 production Chromium browser全過**，0retry/flaky/skip、PASS／exit0；這是本輪重跑原回歸，不稱新功能43。登入／本人密碼／身份／quota／images／taxonomy／pagination、真PG outage503及恢復保持，UI一般故障仍explicit stubs。drill僅為核對待回收bytes關scheduled media cleanup，正常ADMIN endpoint回收另驗，沒有關auth/CSRF。時間fixture非8h soak。
- JAR hash前後固定；V1–V9 git/source/JAR同、原12public bytes保持；source/dist/report hash在JSON。390px taxonomy/editor/audit回歸截圖原尺寸裁切已看，前端本輪未改。unit accounts／三audit／image／upgrade schemas0、3分類／32tech／5作品；八個自有scopes（unit、兩drill各3、完整runtime）containers/volumes/networks與fixture media0、ownJVM/preview退出、六ports TCP拒絕。只有private ignored work的dump/bundle/trace/log保留，沒有停其他服務或提交raw secrets。
- 失敗如實保留：最初up health早於final socket，首role查詢讀取失敗，當時沒有起Maven／沒有role mutation；補readiness後明確再up成功才package99。第一drill通過29個前置/負向checks，成功restore路徑grants fingerprint不符：pg_dump對standard public省略預設PUBLIC USAGE，drop/recreate schema丟它；沒有略過比對/盲目擴權。改保留standard schema／TOC與table defaults，第二drill48過；第一scope均purge，raw/result留work。7 Python兩次均过、package/build一次過、final runtime一次74/43過，無automatic retries／migration checksum改動。

- Git保存：最初默认push缺credential helper，提示无法读取GitHub Username；复核revision369/current/enforced与既有GH_TOKEN SDK，`gh api ... .permissions.push`为true。以单次命令 `git -c credential.helper='!gh auth git-credential' push origin main`正常授权推送3a3b6ba；不读/打印token、不改全局credential／proxy／其他repo。后续同环境push沿用此helper，fetch仍核对实际remote。

### 尚未完成事項

正式app Docker／独立migration／CI、HTTPS／可信proxy／匿名Session及connection節流；備份加密／異地副本／排程保留／容量告警／PITR／RPO-RTO；忘記密碼的免費可信交付／初次強制更改；批次排序／audit完整導出與保留；SeaweedFS/S3／媒體總quota／上傳節流／GC告警；使用者本機/正式資料restore驗收、公開Demo／真實個人經歷與其他來源截圖。

### 已知問題

manual offline snapshot，必须停止所有應用、背景及owner維護寫入；trusted bootstrap/fs owner能繞過NOLOGIN，不是防owner篡改／線上分散式一致性。工具只支援Linux/WSL2、stock helper PG17／標準15表與匹配immutable migration；不可套一般production／其他作品DB。備份內有hash／草稿／audit，private700/600非加密／異地備援；SHA只能驗完整性非來源真偽，不還原第三方SQL/archive。原Git public/JAR/dist/env不打包，另需匹配checkout與素材。每表100000列、dump/media各512MiB／10000objects等為本版驗證上限，非大資料效能；full row fingerprint為小目錄工具。失敗output/scopes保留且不overwrite；突斷/PG故障可能留下NOLOGIN journal，恢復后先核對再啟動。不同頁snapshot／身份配額與POST commit/response邊界等原限制保持；尚未正式公開部署。

### 下一步建議

重新fetch main、讀本交接／AGENTS、核對status/branch/log/diff含staged與ff-only。下一個獨立里程碑是平台app Docker與獨立migration交付：私有runtime secret掛載／最小權限／readiness、同源gateway與static深連結、隔離PG/media volumes、free proxy/CA容器驗證及rollback；HTTPS／可信proxy與加密異地備份再拆，不混其他作品DB或付費服務。保留已發布V1–V9，新schema需求新migration且更新backup契約；忘記密碼免費可信channel另定。每階段package/build/真PG/Browser及HANDOFF、commit/push main、fetch remote/clean，保存优先。

下方為歷史；「PG＋圖片協調backup／isolated restore尚未完成」不是目前狀態。formal backup／deployment仍待。

## 2026-10-13：Phase4G 動態分類／技術治理與公開／管理分頁（本輪最新）

起始remote main `ea187988ad7b1714da77c92fcc491fdadfa15ba8`；fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者授權上一輪建議的分類／技術治理及分頁，只改developer-portfolio。套用cloud runtime，proxy／系統CA／明確local Docker socket與VFS保持；SDK唯讀借用，cache/secret/raw證據留自己的ignored work。資訊壓縮後重新fetch、HANDOFF、status/diff/log核對實際未提交成果再續作；無精確usage介面，完成此獨立里程碑保存，不接新大型階段。

### 本次完成內容

- 分類／技術字典新增／改名／分類排序／未引用刪除：server UUID、version／expectedVersion、strict JSON欄位與真整數、唯一名稱／排序、500項上限、no-op保持版本。ADMIN寫入／VIEWER讀取與最近50筆安全範圍稽核，寫入需CSRF；包含草稿的references與FK阻擋引用刪除，audit保留刪除tombstone。
- 與作品同管理advisory transaction lock、交易內再查actor enabled/ADMIN/version；改名鎖至多100引用作品、完整before/after UPDATE audit及父作品version+1，同字典／taxonomy audit交易。任何audit故障全部rollback；超過100先409、不部分提交。只改分類排序不提高作品版本；開著舊作品或字典的editor409保留輸入，需explicit載最新。既有作品編輯新增技術標籤仍可用，但新增字典CREATE audit與作品同交易、受500上限。
- 前後端移除固定三分類registry，公開只列有published引用的分類；已登錄未引用category filter200空、未知400。管理options包含全部，未知管理category空集合。V9增version／taxonomy_audit與writer分類DML／技術UPDATE/DELETE，其他角色無新audit權，writer audit只能SELECT/INSERT、不能hash/Session/schema。V1–V8不可變；只更新未編輯Phase4F/version7平台seed，四來源作品與既有身份/session保持，無新role／credential或reset。
- 公開及管理列表有界LIMIT/OFFSET，page1–100000、pageSize1–50／default12，穩定sort_order/slug，filtered total／totalPages、公開catalogTotal，同request repeatable-read snapshot；越過末頁items空但total不丟。管理GET列表由array改envelope，frontend一起更新。literal搜索管理另含slug，公開featured／管理published过滤。獨立published下一作品endpoint末筆回首、單筆null／draft404，不依賴前端首12筆。
- 共用分頁controls；公開q/category/page/pageSize URL深連結／reload，搜索／分類／size改變回page1。管理搜索／分類／發布範圍／分頁，取消舊請求與epoch拒絕慢回應；字典管理版本、引用數、編輯／確認刪除與稽核、VIEWER無寫入口。503保留名稱、timeout結果未確認不自動重送；保存／reload失敗分別提示。

### 重要修改檔案

backend/admin/Taxonomy／TaxonomyController／AdminProjects／AdminProjectController、catalog/PageRequest／ProjectCatalog／ProjectController、auth/AdminSecurity、V9；TaxonomyIntegrationTest／AdminProjectIntegrationTest／ProjectApiIntegrationTest；src/api/taxonomy／adminProjects／projects／admin、AdminTaxonomyView／AdminProjectsView／ProjectsView／ProjectDetailView／AdminView、PaginationControls／types/router/styles；tests/taxonomy／admin-projects與scripts/run_persistence_runtime；AGENTS／README與[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)、公開／管理／PG／計畫／來源／待補／VERIFICATION、[安全JSON](verification/taxonomy-pagination-phase4g-2026-10-13.json)及本交接。

### 測試／build結果

- 最終Maven clean package **99真PG17.11／real HTTP全過，failure/error/skip0**（原87＋taxonomy/pagination12）；Vue typecheck／production build通過。沒有H2/mock Security或skip tests。新12：動態分類生命週期／tombstone、技術改名含草稿／父版本、兩種audit故障全rollback含create/delete、no-op/sort-only/stale、真並行200+409、VIEWER/CSRF/匿名/最小grant、strict／重複／version上限、implicit技術audit回滾、分頁total／排序／literal搜索／draft範圍、非法頁碼／未引用分類、next/wrap/draft、500capacity與101引用改名拒絕。
- 兩V2→V9及兩V5→V9真隔離schema升級，保留已編輯平台、來源作品、身份／Session opaque bytes及media，所有upgrade schemas已移除；首次scope V9與same-volume再啟動不reseeding。
- 固定JAR／final dist／新的自己的PG／private root／Chromium151：**74 runtime＋43 browser全過，0retry/flaky/skip，PASS／process exit0**。真HTTP字典新增／兩次改名／父version5與5份project audit，ref409、empty page count、taxonomy版本與audit跨JAR及same-volume PG restart保持，刪除後6份taxonomy audit保留。原登入／身份／本人密碼／quota／圖片bytes／PG outage503與恢復回歸保持。
- 新4browser：UI新分類發布與公開filter／兩字典editor409／父editor409保留輸入／引用刪除409；技術改名／刪除audit、stub503保留名稱與手動核對、390px無溢出；公開／管理size2頁數／深連結reload／搜索reset與慢管理回應不覆蓋；VIEWER UI與真API寫入403。原39全部回歸。一般503/429/到期/不完整UI仍明確stubs，真DB故障／交易rollback／quota另由Java/runtime證明；時間fixture非5分鐘／8小時soak。
- JARhash前後固定；V1–V8 git/source/JAR同、V9 source/JAR同，原12個public bytes保持；source/dist/report hashes在JSON。已看390px完整頁及editor／audit原尺寸裁切。unit scope accounts／三種audit／image／upgrade schemas全0，3分類／32技術／5作品；兩自有scopes containers/volumes/networks/media files全0、own JVM/preview退出、四ports TCP拒絕。原bootstrap兩帳號hash/role保持；runtime合成tombstone／身份fixture至自己volume purge，沒有停別人服務或提交raw秘密。
- 第一package **98 tests／6 failures／0 errors**：合成category prefix含完整UUID使名稱超40，被正確400 guard拒絕；縮短fixture prefix，未放寬產品限制；新增capacity驗證後完整clean package99全過。失敗log/XML保留private work。frontend兩build均通過；最終runtime一次74/43通過，無自動retry／Flyway repair/checksum/reset。

### 尚未完成事項

忘記密碼reset／免費且可信交付／強制初次改密碼；批次／拖曳排序、audit完整导出／容量／保留；SeaweedFS/S3、圖片總quota／上傳節流／GC告警；正式HTTPS／可信proxy／匿名Session與connection節流、app Docker／CI／獨立migration；PG＋images協調backup／隔離restore；公開Demo／個人經歷與其餘来源作品截圖。

### 已知問題

同request count/items使用snapshot，但不同頁請求間新增／排序／刪除可造成位移，沒有cursor／跨頁snapshot；LIMIT/OFFSET及literal search不代表大型目錄效能。首頁只展示預設最多12精選；更多從作品列表查閱。管理列表envelope是client契約升級，舊array client須一併更新；管理filter/page不存URL。字典每種500、每次改名100引用，後台寫入仍序列化，非高吞吐。只有API路徑保證改名version/audit傳播，外部owner直改須自行維護；append-only非防owner篡改或外簽章。recent50不是完整audit導出。既有TLS/proxy、NAT/quota／在途request、POST commit/response不原子、private evidence/本機媒體限制保持；Vite preview不是正式公開部署。

### 下一步建議

重新fetch main／讀本交接與AGENTS／核對Git及ff-only。優先平台PG＋圖片的協調備份與隔離還原演練，驗證content/version/audit、媒體bytes及存取權恢復，與其他作品DB完全隔離；之後formal Docker／HTTPS／可信proxy及獨立migration。忘記密碼需另選真正免費且可交付的受信任管道，不假設環境有Email供應商。排序／audit导出另拆，不混多個大型里程碑。每階段完整package/build/真PG/browser、HANDOFF、commit/push main、fetch remote/clean；交接保存優先。

下方為歷史；「分類／技術與分頁尚未完成」不是目前狀態。

## 2026-10-13：Phase4F 本人密碼更改／持久配額／全 Session 撤銷（本輪最新）

起始remote main `7e37f3f0108e08229595de3b3b5a55b4b6d41795`；fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者授權上輪建議的本人改密碼，不含忘記密碼reset/交付。本輪只改developer-portfolio；套用cloud runtime/docker/network，revision363/current/enforced unrestricted，proxy／系統Java CA／local socket/VFS保持；SDK唯讀借用、cache/secret/raw證據在自己的work。無精確usage工具，完成此獨立里程碑保存、不開下一大型階段。

### 本次完成內容

- ADMIN/VIEWER登入後GET本人安全metadata/版本、POST expectedVersion/currentPassword/newPassword；username只取Session，不接受指定他人。精確型別/欄位、new12–256且不全空白、current非空≤256，驗現有PBKDF2與新舊不同；204空body/no-store，錯誤/衝突/503不回secret或SQL。
- 獨立portfolio_credentials datasource/transaction manager；內部SELECT proof需要身份欄位含hash、UPDATE hash/version、Session principal_name SELECT/DELETE、INSERT安全audit。不能改role/enabled、建立/刪帳號、讀Session attrs／audit、改作品/schema。identity writer仍不能讀改hash，auth仍不能更新hash；HTTP不用owner/bootstrap。
- 使用V6bucket表/password:namespace，與login配額分開；共300秒window/4096總量及短PG admission lock，預設本人5／socket peer20／全站40。驗密碼前已提交配額，成功/錯誤都消耗、重新登入/更改成功/API或PG重啟不清零；429 Retry-After/no-store。明顯格式/CSRF錯誤不做KDF。每JVM改密碼最多2個工作，hash工作不持身份tx鎖。
- 與角色治理同advisory lock，hash後在credentials tx再驗enabled/role/version/oldhash，CAS更新hash/version+1、刪本人所有Session、INSERT PASSWORD_CHANGE安全audit同交易；audit/DB失敗完全rollback。self回應前invalidate current request+清SecurityContext，舊Cookie不重用。並行只有一筆成功／一份audit，另一401/409或busy429；不能改他人或角色。版本上限拒絕。
- V8擴既有audit action欄varchar20及PASSWORD_CHANGE，JSON metadata whitelist保持，四字段snapshot不放hash/明文。V1–V7不改，僅更新未編輯Phase4E/version6平台seed，已編輯/來源作品、既有身份/Session保留、不reset帳密/配額。新scope有credentials private role/env；原V7 scope首次升級先enable-credentials，角色有但matching config缺失拒絕、不重設。已配置scope重跑env bytes不變。
- 本人更改頁、ADMIN/VIEWER概覽入口、三password inputs與確認一致、任何提交結果/切頁清空全部秘密，無browser storage。成功顯示全裝置需新密碼登入；wrong/reused不改身份，429同頁倒數不被reload身份解除、不自動重送；timeout/503明確結果未知先登入核對。account audit frontend認識PASSWORD_CHANGE，mobile390px已檢視。

### 重要修改檔案

backend/auth/CredentialsDatabase／LoginLimits／AdminSecurity、credentials/PasswordChange／PasswordController、application.yml／V8；PasswordChangeIntegrationTest／ProjectApiIntegrationTest；scripts/portfolio_db.py／run_persistence_runtime.py；src/api/password／admin／accounts、AdminPasswordView／AdminView／router；tests/password.spec.ts；AGENTS／README、SELF_PASSWORD_CHANGE及相關身份/PG/來源/計畫/待補/VERIFICATION、[安全JSON](verification/self-password-phase4f-2026-10-13.json)與本交接。

### 測試／build結果

- 最終Maven clean package **87真PG17.11／real HTTP全過，failure/error/skip0**（原77＋password10）；Vue typecheck與production build通過。不是H2/mock Security/skip tests。
- 新10：ADMIN/VIEWER只本人、wrong/reused不變hash/version/Session/audit、CSRF/匿名、unknown target/type/weak/stale version、持久5次配額/重新登入不清/epoch到期恢復、peer/global獨立login、真並行一份成功、audit INSERT故障hash/Session全rollback、service stale role/disable拒絕、credential最小grants及回應/audit不含原/新password/hash。兩V2→V8及兩V5→V8隔離schema真升級保留已有資料與Session/身份opaque bytes，移除所有schemas。
- 固定JAR/dist／新自己的PG／私有root／Chromium151：**65 runtime＋39 browser全過，0retry/flaky/skip、PASS／process exit0**。真VIEWER更改、旧password401、新password登入v2、audit/hash/version與新Session跨JAR及same-volume PG restart保留；耗盡password配額也跨兩種restart保持。credential/auth/identity/public各pool有界readiness恢復，真PG outage503；原media/CRUD/identity/login/absolute期限回歸保持。
- 4新browser：獨立VIEWER新帳號、另browser context同帳號的舊Session被撤銷、舊密碼拒絕/新密碼登入、wrong/reused/不一致清欄與身份保持、stub429倒數/不自動重送、stub503清秘密/結果未確認/390px。一般429/503和原匿名到期為明確stub；真password/quota/DB故障由Java與runtime證明。clock只调disposable epoch/Long，非5分鐘/8小時soak。
- JARhash前後固定；V1–V7 git/source/JAR同、V8 source/JAR同，原12個public bytes不變；source/dist/report hashes在JSON。最終390px截圖已看，三password空。test02 accounts/兩種audit/images/upgrade schema0、media files0；三個自有test/runtime scopes containers/volumes/networks0、media files0（失敗初始化未建media也算無殘留）、自有JVM/preview退出與四ports TCP拒絕。原bootstrap兩帳號hash/role保持，新的合成password/identity accounts與安全audit為預期fixture至自己volume purge，沒有停其他作品/提交raw秘密。
- 失敗保留：credentials helper複製identity邏輯時uppercase env key漏改，up拒絕且未建立新role；當時仍啟動Maven，造成V8缺role/Flyway context初始化27 errors，功能斷言未有效執行。保留raw log/XML，精確改PORTFOLIO_CREDENTIALS，自己的失敗disposable scope purge；新test02先确认up exit0再clean package87全過。沒有改migration checksum或repair/baseline/reset舊DB，runtime一次65/39通過，沒有automatic retry。前端build沒有失敗。

### 尚未完成事項

忘記密碼的reset／一次性交付／Email／強制初次改密碼、身份事件告警/保留/完整導出与容量；category/tech治理、管理列表分頁；SeaweedFS/S3 adapter、圖片總quota/上傳限流/GC告警；formalHTTPS/可信proxy/匿名Session及connection節流、app Docker/CI/獨立migration、PG+images協調backup/隔離restore、公開Demo/個人經歷/來源作品截圖。

### 已知問題

本人更改不等於忘記密碼重設或他人admin reset；metadata audit不含secret、不代表防owner篡改或external signature。credential runtime需要內部讀hash才能驗證/CAS，使用者API不能查hash；其DB secret/Session序列化需受信任。全Session撤銷保證後續請求身份版本核對，不取消已有在途其他領域request。POST commit/response非原子，timeout/503結果須手動核對，不自動重送。配額固定window/NAT共peer／SHA推測／cold pool及8h fixture而非soak限制維持；正式TLS/proxy防護尚待。部署V8須先enable-credentials，不能用舊env直接起新JAR；不要在up失敗時直接執行build。原local images/私有證據/mock/DIRTY/品質與本機真服務限制不變，Vite preview非公開部署。

### 下一步建議

重新fetch main/read此交接與AGENTS/status/log/diff含staged/ff-only。優先category/technology治理及公開/管理分頁：替換固定分類registry的限制、精確writer權限、version/audit與刪除/引用約束、穩定排序/搜尋契約及mobile驗收；先獨立可驗證里程碑，不混其他作品DB。忘記密碼reset交付後續須選免費且實際可用渠道，最後再formalDocker/HTTPS/可信proxy/協調備份還原。每階段package/build/真PG/browser、HANDOFF、commit/push main、fetch remote/clean；保存交接优先於新大型改動。

下方為歷史；「本人改密碼尚未完成」不是目前狀態。

## 2026-10-13：Phase4E 帳號／角色治理與最後管理員保護（本輪最新）

起始remote main `3fdfbe8d24b93ce14a4667e203b604a260342a3a`；fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only、乾淨main。使用者授權上輪建議的帳號角色與最後ADMIN保護。本輪只改developer-portfolio；cloud runtime/Docker/network指引，revision362/current/enforced unrestricted，proxy／CA/local socket/VFS保持；SDK唯讀借用，cache/secret/raw evidence留自己的work。無精確usage介面，完成此獨立身份里程碑先保存、不接密碼重設大型修改。

### 本次完成內容

- ADMIN帳號目錄／新增（username＋初始password＋ADMIN/VIEWER＋enabled），PUT角色／enabled／expectedVersion及最近50筆身份稽核；VIEWER無目錄／identity audit讀寫權，原作品VIEWER權限不變。username immutable、無delete/reset；初始密碼12–256、PBKDF2、最多2個同時Hash、API建立最多100帳號，不回/稽核明文或hash。
- 獨立identity datasource/transaction manager及新portfolio_identity role；只讀非秘密身份欄位、INSERT建立必要欄位含hash、UPDATE role/enabled/version、SELECT principal_name/DELETE Session、SELECT/INSERT安全audit。不能讀/改hash、刪帳號、讀Session attributes、改作品/schema或改刪audit。reader/auth/catalog writer不能讀identity audit，HTTP不用owner/bootstrap。
- short try-advisory transaction lock序列化身份寫入；actor當前role/enabled/version在交易再查，stale actor401；目標expectedVersion stale409無audit。最後enabled ADMIN不能停用或降權，真並行两人各自降權只有200+409，保留一位。no-op不增加版本/audit或撤銷；版本上限拒絕。
- 有效更改version+1、刪目標全部PG Session、INSERT只有username/role/enabled/version的前後snapshot同交易，任一步含audit故障全rollback。self更改回應前另invalidate current request/清SecurityContext，防JDBC尾端重存；每次新請求仍用version/enabled/role拒絕舊身份。
- V7新private identity audit、JSON secret key whitelist、精確grants，原V1–V6 immutable；只更新version5/Phase4D原seed平台自介，已編輯平台及四來源不覆寫、既有hash/Session bytes保留。helper新scope有private身份role/env，既有scope首次V7前enable-identity；角色已存在但設定缺失拒絕，不reset密碼/data。已配置scope重跑env byte-for-byte不變。
- UI帳號列表／建立／單帳號editor／confirmation／安全audit、409保留role/enabled並explicit載最新、503保留代號但清初始密碼、網路結果未知不自動重送、self變更需重登；建立前提醒保存及受信任交付密碼。no-op顯示保持登入；已保存但list reload失敗不誤稱保存未成功。390px完整頁已查看，既有320/390px回歸保持。

### 重要修改檔案

backend/auth/IdentityDatabase／AdminSecurity、identity/AccountManagement／AccountController、application.yml／V7；AccountGovernanceIntegrationTest／ProjectApiIntegrationTest；scripts/portfolio_db.py／run_persistence_runtime.py；src/api/accounts／admin、AdminAccountsView／AdminView／router；tests/accounts.spec.ts／portfolio.spec.ts；AGENTS／README、ADMIN_ACCOUNTS及相關身份/PG/計畫/來源/待補/VERIFICATION、[安全JSON](verification/account-governance-phase4e-2026-10-13.json)及此交接。

### 測試／build結果

- Maven clean package **77真PG17.11／real HTTP測試全過，failure/error/skip0**（公開/升級21、auth11、CRUD13、media12、login10、identity10）；Vue typecheck＋production build通過。沒有H2/mock Security/skip tests。
- 新10驗真建立與初始hash登入、安全response/audit、匿名/viewer/CSRF拒絕、stale/duplicate/invalid scalar/unknown/weak password、最後ADMIN/no-op、兩個並行self降權200+409、role/disable/reenable撤銷多Session不改hash、self Session不重生、service內stale actor、audit INSERT故障create/update/Session刪除rollback、最小role及DB secret key拒絕。兩V2→V7與兩V5→V7真schema升級保留已編輯平台、來源、account/session opaque bytes和media；全部schema刪除。
- 固定JAR/dist、全新自己的PG／私有root、Chromium151：**56 runtime＋35 browser全過，0retry/flaky/skip，PASS／process exit0**。真HTTP建立/初始密碼登入/VIEWER拒目錄/停用/撤銷；治理version/hash/audit跨JAR及same-volume PG restart保留。PG outage503、identity pool另有界readiness；原media/CRUD/登入限流/absolute expiry回歸仍過。
- 4新browser包含建立→停用→啟用安全audit、兩頁409保留輸入與最新、唯一root保護/VIEWERUI及真403、stub503清密碼/保留代號/手動恢復/390px；正常走真PG。UI503、原429/匿名到期為明確stub，真正audit DB故障/expiry/quota另由Java/runtime證明；調clock只disposable fixture，非8h soak。browser新增合成帳號與identity audit為預期寫入，保留到自己volume purge；只核對原runtime_admin/runtime_viewer的hash/role不被改，domain/history baseline保持。
- JARhash前後固定，V1–V6 git/source/JAR同、V7 source/JAR同、原12個public檔bytes不變；source/dist/report hash記安全JSON。最終390px已人工看，初始密碼空。test scope account/identity audit/project audit/images/upgrade schema0與objects/incoming0；兩個自有scopes container/volume/network全0、media files0、自有JVM/preview退出、25486/25487/19186/15186無listener/TCP拒絕。沒停其他作品／没提交raw credential/log/cookie。
- 此輪test/build/runtime皆成功、無automatic retry；first frontend build後只修UI no-op訊息與保存後reload失敗語義，再production build後才固定產物runtime，沒有在測試中重建JAR/dist。SDK/依賴cache只在自己的ignored scope，沿用proxy/CA。

### 尚未完成事項

密碼更改/重設、一次性交付/強制首次改密碼、身份敏感事件/限流与完整audit導出；分類/tech治理及分頁；SeaweedFS/S3 adapter、圖片quota/上傳限流/GC告警；formal HTTPS/可信proxy/匿名Session端點及connection節流、app Docker/CI、獨立migration啟動、PG+images協調backup/隔離restore、公開Demo/個人資料/其他作品新截圖。

### 已知問題

初始密碼由ADMIN手動設定/受信任交付，沒有更改/reset或自動Email，不可冒稱完整身份產品。API100帳號與audit最近50、無分頁/導出，CLI初始provisioning不經HTTP治理/audit；owner/bootstrap直接SQL屬信任邊界。Audit append-only限runtime、沒有外部簽章/保留政策。撤銷保證後续請求身份核對，不宣稱取消早已在處理的其他領域請求；身份服務自身交易會再次核對actor。private identity credentials新增需upgrade先enable-identity，不用舊env直接啟V7。既有quota fixed-window/NAT/cold pool、media local adapter、正式HTTPS及來源作品私有/mock/DIRTY/品質限制保持。Vite preview非正式部署，viewport非真機。未有API100上限負載/容量壓測，不把count guard當正式容量驗收。

### 下一步建議

重新fetch main、read本交接／AGENTS/status/log/diff含staged/ff-only。先小型密碼更改里程碑：本人提供現有密碼＋新密碼、成功增加version並撤銷全部Session、必要secret-free事件稽核及限流；不能查或回舊hash/明文。忘記密碼的重設/一次性交付另拆，依免費實際可用管道再做。之後category/tech治理與分頁，再formal部署/proxy/協調backup還原。每可驗證里程碑package/build/PG/browser、HANDOFF、commit/push main、fetch remote/clean核對，保存優先於新大型實作。

下方是歷史；「帳號角色治理尚未完成」不代表目前狀態。

## 2026-10-13：Phase4D 持久登入限流與 Session 絕對期限（本輪最新）

起始remote main `69bfa4915722a71f7ed30547f115a47077defb8c`；fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者同意上輪建議的登入限流與Session絕對期限。本輪只改developer-portfolio，套用cloud runtime／Docker／network指引，revision360/current/enforced unrestricted、proxy／系統Java CA／local socket/VFS保持；JDK/Maven唯讀借用、cache/secret/raw logs/合成圖留本repo work。沒有精確usage介面，完成此獨立里程碑先保存，不接大型帳號治理。

### 本次完成內容

- 免費PG `admin_login_bucket`持久配額：每300秒帳號30／socket peer60／全站120次，成功或失敗都計入、成功不清零；短auth transaction＋try advisory lock串行原子admission，鎖忙429/1秒，不持鎖做PBKDF2。SHA256 bucket key、不存原username/IP/token，過期清理、最多4096rows，密碼工作每JVM最多2個；不是無限佇列。
- CSRF成功後才計數，invalid長度／空帳密通用401；超額429 LOGIN_RATE_LIMITED／整數Retry-After/no-store，不區分存在／未知帳號。auth DB／配額讀取失敗503，不fallback；相同PG跨JAR或same-volume PG restart不重設已用配額。
- server.forward-headers-strategy=none、只用實際request remoteAddr，忽略XFF／Forwarded／X-Real-IP；啟用framework/native會拒啟動。尚未做可信CIDR/跳數；proxy後訪客共用peer配額，正式部署另驗。
- 30分鐘閒置保留；成功真密碼認證寫PG Session login-time Long，8小時絕對到期，活動／GET session／重啟不刷新。缺舊marker／未來／非法時間fail closed；先invalidate+清SecurityContext，protected讀/寫401，session端點匿名+新CSRF，舊Cookie不能重放；只有重新成功密碼認證才延長。
- V6僅新增private counters精確auth DML，reader/writer無讀取、auth仍不能改身份／作品；V1–V5完全不改。僅更新version4+duration/status吻合的未編輯平台seed，已編輯內容、四來源作品與已有account/Session bytes保留，不seed/reset身份。
- 前端429清空密碼／保留帳號、倒數禁用submit、重新載入驗證不清倒數、不自動重送；到期導回登入清舊概覽。ADMIN安全概覽顯示idle/absolute期限與三項實際門檻，VIEWER不可讀。手機390px倒數與既有320/390px回歸保留。

### 重要修改檔案

backend/auth/LoginLimits／LoginProtectionFilter／AbsoluteSessionFilter／AdminSecurity／AdminController／AuthResponses、application.yml／V6；LoginSafetyIntegrationTest／ProjectApiIntegrationTest；src/api/admin、AdminLoginView／AdminView；tests/admin.spec.ts／portfolio.spec.ts、scripts/run_persistence_runtime.py；AGENTS／README／LOGIN_SAFETY、ADMIN_AUTH／PERSISTENCE／PROJECT_CATALOG／PROJECT_PLAN／CLOUD_LOCAL_FOLLOWUP／VERIFICATION、[安全JSON](verification/login-safety-phase4d-2026-10-13.json)與此交接。

### 測試／build結果

- 最終Maven clean package **67真PG17.11／real HTTP測試全部通過，0failure/error/skip**（公開／升級21、auth11、crud13、media12、login safety10），沒有H2／mock Security／skip tests。Vue型別檢查與production build成功。
- 新10項涵蓋帳號/peer/global/容量、真並行最後配額slot 401+429／不超計數、header偽造與不同Cookie／帳號不可繞過、CSRF不消耗、過期window恢復、auth限流讀取權故障503與reader/writer拒讀、活動不續期、絕對到期先拒寫入及old Cookie、新認證重建、缺marker／未來timestamp與重登；V5→V6兩個真隔離schema驗證auth/session opaque bytes、media關聯、四來源與已編輯平台保持。較早V2→最新兩schema仍保留資料，schema均刪除。
- 最終固定JAR/dist、全新自己的PG／私有root、Chromium151：**50 runtime＋31 browser全過**，0retry/flaky/skip、PASS／exit0。預設browser配額保持啟用；真JAR/PG restart保留原login-time與耗盡quota，真PG outage503，真absolute expiry／舊Cookie撤銷／重新認證。PG/media/CRUD既有重啟與停機回歸保留，domain/history與account hash不被UI/startup修改。
- 時間驗證明確由owner只修改disposable bucket epoch／serialized Long，不是等滿5分鐘或8小時；runtime另用更嚴的3/5/8配額快速耗盡與重啟，browser使用預設30/60/120。一般UI429與匿名到期、既有503為明確route stub；真拒絕／expiry／PG故障由Java及固定產物runtime證明，兩類證據不混稱。
- 最終JARhash前後相同、V1–V5 git/source/JAR bytes同，V6 source/JAR同、原public素材bytes不變；安全source/dist/report hashes見JSON。最終390px倒數截圖已人工查看。兩test scopes accounts/audit/images/upgrade schemas0、objects/incoming0；7個非身份秘密quota rows隨自己disposable volume purge，非應用啟動reset。
- 本輪五個自有test/runtime scope container/volume/network全0、media objects/incoming0、自有JVM/preview停止及25486/25487/19186/15186無listener／TCP拒絕；原始證據與私有env留work，未停其他作品服務。
- 先66測試／47runtime／31browser全過，發現平台自介仍寫限流待做，追加本輪尚未發布V6的精確seed更新與V5 upgrade preservation test，再以全新scope clean package67及固定產物50/31驗證；舊證據保留、未repair/reset既有scope、未在runtime中重建JAR/dist。首runtime瀏覽器尚未啟动時先修正匿名到期stub的測試fixture（真backend logout後才重新登入）、390px倒數截圖；不是把stub當實際到期。最終候選runtime的browser31過，但第二次PG restart僅等public health而auth pool仍503，quota.session()即失敗，完整run FAIL/exit1保留；補上各PG recovery後有界35秒、只讀session readiness，逐次status記錄，不retry登入／寫入／整個assertion，使用相同JAR/dist與新scope重驗50/31。重跑啟動前bare bind又遇前輪TIME_WAIT（實查无listener、没有建立scope），原log保留；preflight加SO_REUSEADDR允許已關閉TCP的TIME_WAIT，仍拒絕實際listener／strictPort、不停別人服務，再用新目錄固定產物完整重驗。Maven/build未失敗，沒有automatic test retry。

### 尚未完成事項

帳號／角色管理、最後ADMIN保護、密碼重設與身份變更敏感稽核；分類/tech管理與分頁、audit導出／drag排序；SeaweedFS/S3 adapter、圖片總配額／上傳限流／GC告警；formal HTTPS／trusted proxy/perimeter/匿名Session節流、app Docker／CI、獨立migration啟動、PG+images協調backup/隔離restore、公開Demo／個人資料与其他作品真截圖。

### 已知問題

固定window邊界可burst，SHA key可字典推測不是匿名資料；攻擊者可耗盡帳號/NAT短期quota，不宣稱401 timing完全等價。proxy未可信解析、全代理共用peer；GET CSRF可建立anonymous Session、CSRF拒絕不耗quota，正式perimeter限流／TLS尚待。quota只是管理登入低流量場景，沒有告警與真流量容量驗收；PG剛恢復時兩個獨立pool可不同步恢復，短期AUTH_UNAVAILABLE503合理；runtime必須等public和auth readiness且有時限，不能只用public health判定登入可用。主機絕對時間須同步，多實例policy必須一致，window參數放大無法還原已清bucket。舊無marker Session需重登，是有意升級行為。fixture調時間非8h soak／突然斷電或正式restore驗收。Local media/GC／source私人證據/mock/DIRTY／品質等前輪限制保持，Vite preview非公開部署。原始credential/cookie/trace不能提交。

### 下一步建議

重新fetch main/read此交接／AGENTS/status/log/diff含staged/ff-only。先獨立帳號治理里程碑：ADMIN限定帳號／角色/enabled、expectedVersion、最後enabled ADMIN保護與真並行交易、version變更立即撤銷Session；身份寫入獨立最小權限與只存必要非敏感稽核，不存明文/hash/重設token。密碼重設交付可再拆，勿混正式proxy或其他作品身份。之後taxonomy/分頁，再正式Docker／HTTPS／備份還原。每里程碑完整package/build/PG/browser、HANDOFF、commit/push main和remote/clean核對；交接優先於新大型實作。

下方為歷史；「登入限流／絕對Session期限尚未完成」不是目前狀態。

## 2026-10-12：Phase4C 圖片上傳／媒體版本／安全回收（本輪最新）

起始remote main `8a893b878832a76496332fa448a6b2c4c3ebe8c2`，fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者同意「圖片上傳與素材治理」，本輪完成此里程碑；工作中「繼續」為同一目標。只改developer-portfolio，讀cloud runtime/docker/network指引，revision278/current/enforced unrestricted、proxy／系統Java CA／local socket/VFS保持；SDK唯讀借用、cache/secret/logs/合成圖在自己的work。沒有精確usage介面，先收尾保存，不開身份大型任務。

### 本次完成內容

- 免費ImageStore／LocalImageStore adapter、獨立private root，原filename不作path、server UUID＋固定png/jpg、marker／no-symlink／Unix700/600。不串接其他作品storage，也沒有SeaweedFS服務。
- 只收PNG/JPEG、原檔5MiB／request6MiB、真signature/MIME/decode、dimension4096／8MP、重編碼≤10MiB與SHA256/bytes；非圖片／SVG／損壞／超限拒絕。canonical檔先fsync／atomic move再writer metadata/version/audit交易，rollback刪新檔；crash未知物件24h grace回收。
- V5 image_asset／media FK／精確writer grants；媒體aggregate expectedVersion與UPDATE audit同交易，封面替換、截圖append/metadata排序／移除，原static和placeholder不自動遷移。V1–V4不改，V5僅更新仍符合原seed/version的平台介紹。
- public image每次查published/current引用及live asset，再核對bytes/hash；draft/移除404，PG或file故障503，no-store/nosniff。admin preview需ADMIN/VIEWER。parent刪除／換cover／移除時同交易記retired_at；無引用才reclaim，purged_at journal可重試，audit與asset metadata保留。
- 每分鐘排程／ADMIN手動回收，retired/未知object/舊incoming各batch50；24h grace不清最近file，symlink跳過／拒絕。未知file不影響仍引用的asset。Local root納入private scope/env，enable-media給既有scope追加設定，不reset任何密碼／資料。
- 獨立圖片頁：upload／kind／alt／caption、真圖預覽、version衝突保留輸入、二次移除、manual reclaim、VIEWER唯讀，手機320/390px。core文字編輯仍保留media，不用圖片成功結果覆蓋未儲存文字。fixture由驗收工具自行產生，fresh clone可重跑。

### 重要修改檔案

backend/media（adapter／normalizer／reader／schedule／storage error）、admin/ImageCatalog／MediaProjects／Controller／Maintenance與AdminProjects、AdminSecurity／CatalogErrors、application.yml／V5；MediaIntegrationTest與既有三suite；scripts/portfolio_db.py／run_persistence_runtime.py；src/api/media／admin／adminProjects、AdminProjectMediaView／editor／AdminView／router／styles；tests/media.spec.ts／portfolio.spec.ts；AGENTS／README／MEDIA_MANAGEMENT與相關persistence/API/plan/catalog/followup、VERIFICATION、[安全JSON](verification/media-phase4c-2026-10-12.json)及此交接。

### 測試／build結果

- 最終Maven clean package **56真PG／real HTTP測試全過**，0failure/error/skip（公開升級20、crud13、auth11、media12）。沒有H2／mock storage或skip tests。Vue型別與production build成功。
- media12驗PNG/JPEG、filename忽略／尾部payload去除／fake/MIME/dim/5MiB413、CSRF/VIEWER、draft→publish→unpublish、stale/foreign media拒絕、metadata保留bytes、cover替換retirement、audit/IO rollback、missing/tamper503、GC retry/idempotence/24h orphan+temp／symlink不外讀。自己的fixture帳號／audit／assets／upgrade schema最后0，objects/incoming0。
- 最終同JAR/dist、全新PG/私有root、Chromium151：**31 runtime＋29 browser全通過，0retry/flaky/skip、outcome PASS／exit0**。真upload metadata與相同bytes跨JAR及same-volume PG restart保存，真PG outage不送圖片绕過publication gate；刪除立即404、queue清file但audit留存，catalog/history七表baseline相同。新UI5流程包含upload/publish/edit/remove/GC、兩頁409、真fake PNG／stub503、viewer、手機真圖；一般browser503仍stub，不混成真IO故障。
- JARhash前後固定；V1–V4 git/source/JAR完全同，V5 source/JAR同、所有原public檔案bytes不变。最終390px預覽已人工查看。三個自有test/runtime scopes container/volume/network0、objects/incoming0；自己的JVM/preview停止，25486/25487/19186/15186無listener且TCP拒絕。private evidence留work，沒有other作品改動。
- 初次44回歸1fail是upgrade assert期待Phase4B status「管理登入」，改驗V5「驗證圖片上傳」後新56過，沒放寬migration/data保留契約。第一次runtime31/browser29過，但fixture依賴work，不利fresh clone；改自生同bytes合成PNG、新scope固定產物重验31/29，未重建測試中的JAR/dist。runtime result now PASS/FAIL/exit0明确。原logs保留，無automatic retry。

### 尚未完成事項

登入限流／帳號角色治理／密碼重設／Session絕對期限；category/tech治理／management分頁／audit導出／drag排序；SeaweedFS/S3 adapter、總容量配額／上傳限流／GC告警面板／格式擴充；formalHTTPS/trusted proxy／app Docker／CI、PG+images協調備份/離機restore、公開Demo、個人真實內容與來源作品新截圖。

### 已知問題

local adapter非跨資源原子commit，rollback/crash IO殘留透過24h未知file sweep；重啟實證不等於突然斷電或協調restore驗收。JPEG重壓縮、EXIF剝除，不做orientation修正；僅PNG/JPEG。原static圖仍public，移除不刪Git檔；audit保存metadata、退役後歷史src不保留bytes。排程失敗保持queue重試但未告警，manual API可看503；每輪batch50、無總量quota，後台低流量。health既有契約只證明PG，不算file/disk全面健康。明確roles/CSRF防護已在，但formal身份限流／HTTPS仍待；Cookie Secure預設true，只loopback演練false。runtime固定ports/TIME_WAIT需核對，必須先完成build再跑固定JAR/dist，不在測試途中重建。來源私人證據／mock／DIRTY／品質限制保持。

### 下一步建議

重新fetch main/read本交接及AGENTS/status/log/diff含staged/ff-only。Phase4D先獨立身份安全里程碑：免費登入暴力嘗試／rate limit（尊重可信proxy、不信任任意X-Forwarded-For）、Session絕對到期、錯誤泛化及測試；帳號角色／reset治理可拆後續，最少保護最後ADMIN、角色/密碼更動version與撤銷、敏感稽核不存hash/明文。之後category/tech分頁与正式Docker/HTTPS、storage/DB協調備份還原。不改其他作品／不直接公開身份服務；每里程碑build/PG/browser、HANDOFF、commit/push main及remote/clean，交接優先於新大型修改。

下方為歷史；「尚無圖片上傳」不是目前狀態。

## 2026-10-12：Phase4B 作品CRUD／版本衝突／同交易稽核（歷史）

起始remote main `7f1d30b88dc35c71c392d630e02c0dc3bff89cc6`。fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main；使用者同意上一輪建議的作品CRUD／version／audit。只改developer-portfolio；讀cloud runtime/docker/network指引，revision269 current/enforced unrestricted、proxy／系統Java CA／local socket/VFS保持，SDK唯讀借用、cache／secret／logs留自己work。無精確usage介面，保守收尾此獨立里程碑，不接圖片大型修改。

### 本次完成內容

- ADMIN可新增／修改／draft-publish／精選／獨占排序／刪除；VIEWER查看含草稿列表／詳情／稽核，伺服端拒絕寫入。公開列表／分類／搜尋／詳情仍讀published，draft/下架404；公開寫入405與既有版型保持。
- V4新增private project_audit及精確writer grants，独立datasource/transaction manager；writer只能domain必要DML、tech INSERT、media DELETE清關聯、audit SELECT/INSERT、身份非hash欄位SELECT。reader/auth不增加作品寫入／audit讀取權；不用owner/bootstrap作HTTP寫入。
- transaction advisory lock配置排序、row lock比對expectedVersion；更新version+1，stale更新／刪除409無新audit。稽核actor取Session、整份前後snapshot與作品/關聯同交易；任一audit失敗全部rollback。刪除保留audit與slug，防止delete/recreate ABA；排序到上限409不溢位。共享tech字典保留未使用標籤。
- 管理作品列表、文字／技術／功能／挑戰／架構／驗證／限制／HTTPS來源編輯、發布checkbox／排序、二次刪除確認及最近50筆稽核。409保留輸入、明確「載入最新版本」才替換；逾時提示結果未確認、不自動重送。existing cover/screenshots/localDemo修改時保留；沒有圖片上傳或檔案刪除。
- V1/V2/V3不可變；V4僅更新仍符合Phase4A原seed/version的平台介紹，已編輯內容與四來源作品保留。新scope初始化writer role，原scope明確enable-writer，不重設已有密碼／資料。

### 重要修改檔案

backend/admin（ProjectContent／AdminProjects／Controller／WriterDatabase）、共享ProjectProjection／ProjectCatalog、AdminSecurity／CatalogErrors、application.yml／V4、AdminProjectIntegrationTest與兩個既有test；scripts/portfolio_db.py／run_persistence_runtime.py；src/api/adminProjects／admin／projects、AdminTextList／AdminPairList、三個管理views／AdminView／router／styles；tests/admin-projects.spec.ts／portfolio.spec.ts；AGENTS／README／ADMIN_PROJECTS、相關plan/persistence/API/catalog/followup、VERIFICATION、[安全JSON](verification/project-management-phase4b-2026-10-12.json)與本交接。

### 測試／build結果

- 最終Maven clean package **44真PG／real HTTP全部通過**，failure/error/skip0：公開／升級20、登入授權11、管理13。不是mock DB／Security或skip tests。前端vue-tsc與production build成功。
- 管理測試涵蓋完整生命周期／stale PUT&DELETE／真並行HTTP200+409／CSRF&viewer拒絕／duplicate slug&sort／欄位URL/unknown body／稽核權限故障rollback create/update/delete含tech關聯／已存在media/localDemo保留／writer與audit最小權限／最大排序／刪除slug保留。fixture帳號、audit与兩個upgrade schema最後0。
- 最終固定clean JAR與同dist、全新自己的PG、Chromium151：**24 runtime＋24 browser全過，0retry/flaky/skip、exit0**。CRUD version與audit經JAR restart及same-volume PG restart保存，真PG stop health/public/auth503、恢復登入與資料；temporary HTTP project刪除後audit保存，catalog/history七表baseline不變。Browser增加完整UI生命周期／兩頁409保留輸入／viewer UI&真API／503保留表單與手動重試／手機編輯；一般503仍明確stub，未混稱真PG故障。
- stage check另抓到新ProjectProjection一個trailing space，純格式移除後以JDK21 -g/-parameters recompilation確認class與已驗JAR byte-for-byte相同，更新安全source hash；git diff --cached --check通過。沒有重建已驗產物或新增功能。
- V1/V2/V3 git/source/JAR bytes相同、V4 source/JAR相同，無舊JSON。final JARhash前後固定、dist/sourcehash記安全JSON；已人工看最終390px編輯截圖。三個自己的test/runtime scopes container/volume/network全0，自有JVM/preview停止、25486/25487/19186/15186無listener並拒絕TCP。existing enable-writer/prepare_roles保持private env byte-for-byte，不reset/reseed；其他作品不動。
- 失敗保存：初次projection重用少WHERE/AND空白導致public回歸9fail/1error，修正後新44全過；Vue union required存取typecheck失敗，修正未放寬型別。早期native24/browser24雖過，但程序尚未完全退出時開始clean rebuild、移除JAR，使artifact核對FileNotFound而整run exit1；這份不採最終證據。等待build全部完結後固定JAR/dist、新scope重驗exit0與hash；失敗logs保留，沒有automatic retry掩蓋。

### 尚未完成事項

圖片上傳／object素材治理、category/tech管理、管理list分頁／audit完整導出／拖曳或批次排序；帳號角色治理／密碼重設／登入限流／絕對Session期限；formalHTTPS/trusted proxy／app Docker／CI、持久資料備份/離機restore、公開Demo、個人真實內容與來源作品新截圖。

### 已知問題

稽核UI呈現重點欄位／最近50筆，API回傳完整snapshots；append-only只限制runtime，不防owner改資料，未外部簽章。刪除不刪public檔案／儲存物件、shared tech可留孤兒字典項；圖片治理另做。網址不可改且刪後不可重用；排序位置不能重複，交換兩作品須先選空位，尚無拖曳。類別仍前端四註冊型別／seed三分類、management list無分頁；Jackson可coerce scalar，語義及unknown欄位另驗。正式TLS／限流／migration獨立啟動與backup尚待驗，Cookie Secure預設true只loopback演練false；Vite preview不是公開部署、viewport非真設備。runtime JSON passed只計已過checks，判斷整體須看process exit0與artifacthash；測試執行時不得重建同JAR/dist，原始logs/secret不提交。來源作品私人證據／mock／DIRTY／品質限制保持。

### 下一步建議

重新fetch遠端main/read本交接与AGENTS/status/log/diff含staged/ff-only。Phase4C先圖片上傳與素材治理：免費、自有storage adapter／隔離目錄或bucket、型別/大小/實際圖片驗證、不可遍歷路徑／執行檔、媒體metadata版本及同交易audit、物件失敗cleanup與關聯刪除後安全回收；保留public引用與VIEWER唯讀。不要接其他作品storage/DB或正式發布。另獨立身份限流／帳號治理里程碑、正式部署與备份後續。每一個可驗證里程碑build／PG&browser／HANDOFF／commit/push main／remote&clean；不在長任务最後才第一次保存。

下方是歷史階段；「尚無作品CRUD」不是目前現況。

## 2026-10-12：Phase4A 管理登入／Session／角色權限（歷史）

起始remote main `5011401d7335e879a691412f22ba0dc9db26b101`，fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only，乾淨main。使用者同意「先管理員登入與權限，再作品CRUD」，本輪只完成前者。套用cloud runtime/docker/network指引，revision269/current/enforced unrestricted，保留proxy、系統Java CA與local socket/VFS。只改developer-portfolio；JDK/Maven唯讀借用，cache/log/env在本repo ignored work。無精確usage介面，保守完成此獨立里程碑後保存，不開大型CRUD。

### 本次完成內容

- Spring Security＋Spring Session JDBC；30分鐘閒置期限、HttpOnly／Strict／/api/admin Cookie（Secure預設true，loopback演練explicit false），登入旋轉Session與CSRF、登出撤銷、無JWT/storage credential。
- V3新增admin_account及Session兩表；public reader不讀身份/session，獨立auth datasource/transaction manager僅身份SELECT與session DML，不能作品／身份／schema寫入。V1/V2 bytes不變，不seed管理帳號；V3只更新仍符合原seed/version的本平台介紹，不覆寫已編輯資料。
- ADMIN／VIEWER由後端授權：兩者看管理概覽，只有ADMIN看安全概覽；匿名401／越權403／CSRF403／帳密統一401／PG故障503 AUTH_UNAVAILABLE。每次管理請求核對角色、enabled、version，舊Session立即失效；不信client role。
- helper明確create-account，隨機密碼與PBKDF2私有建帳檔，不印／提交密碼、不重設已有帳號；enable-auth可為原Phase3自有scope加auth role/env。重啟不reseed、不重設帳密。
- Vue管理入口／登入／概覽／登出；失敗清空舊管理資料、明確重試、密碼錯誤後清空、不自動重送登入。public layout與API契約保留，手機320/390px可用。

### 重要修改檔案

backend pom／application.yml／auth/*／CatalogErrors、V3 SQL、AdminAuthIntegrationTest／ProjectApiIntegrationTest；scripts/portfolio_db.py／run_persistence_runtime.py；src/api/admin.ts、AdminLoginView／AdminView、router／footer／styles；tests/admin.spec.ts／portfolio.spec.ts；AGENTS／README／ADMIN_AUTH／PERSISTENCE／PUBLIC_API／PROJECT_PLAN／CATALOG／CLOUD_LOCAL_FOLLOWUP／VERIFICATION與[安全JSON](verification/admin-auth-phase4a-2026-10-12.json)。

### 測試／build結果

- 最終Maven clean package **31真PG／real HTTP測試全過**，0failure/error/skip；其中公開及升級20、登入授權11。不是H2／mock Security／skip tests。前端型別與production build成功。
- 最終clean JAR與dist、全新PG、Chromium151：**18 runtime＋19 browser全部通過**，0retry/flaky/skip。真JAR restart保留登入與七表catalog/history fingerprint；真PG stop health/list/auth503，same-volume start恢复原資料與登入；登出後舊Cookie401。CLI建帳hash真登入、預設Secure Cookie實驗及帳號未重設均過。
- 兩個只屬portfolio_test的獨立schema跑真Flyway target2→V3，原seed更新、已編輯平台整列保留、其餘四作品完全相同、不seed帳號、再次migrate0；schema均移除。原V1/V2 git/source/JAR一致、V3 source/JAR一致，舊JSON無殘留。已檢視最終合成手機管理頁。
- 四個自己的test/runtime scopes全部container／volume／network0；所有自己的API／preview退出，25486/25487/19186/15186無listener、實際TCP連線拒絕。private證據保留work，其他作品服務不動。existing enable-auth/prepare_roles實測保持private env bytes且不reset/reseed。
- 首auth測試30中1fail是過期fixture只改expiry_time（清理欄位），修正last_access_time（真正閒置判斷）後31全過；失敗logs保留。早期cleanup audit在fixture仍執行時檢查而未過，完成後確認0帳號／0暫時schema。最終bare bind遇TIME_WAIT，改查實際listener與TCP拒絕確認停止，未停止其他服務。完整runtime兩份均18/browser19通過，最終artifact hash固定。一般browser503仍stub，與真PG outage分開記錄。

### 尚未完成事項

作品CRUD、draft/publish/sort、version衝突及操作稽核、精確writer DB帳號、圖片／物件治理；帳號角色管理／密碼重設／登入限流／絕對Session期限；正式TLS/proxy/app Docker／CI、持久資料備份/離機restore、正式Demo、個人真實內容及其他作品截圖。

### 已知問題

目前只提供管理概覽，不可編輯作品或管理帳號；開發CLI建帳不算正式帳號治理。正式對外前須補登入暴力嘗試防護、HTTPS／trusted proxy與獨立migration流程，Cookie預設Secure在HTTP不會送出，只有loopback可explicit false。Session採JDBC序列化，DB／session writer必須保持受信任；future身份變更須增加version，不修改已發布migration。分類前端仍四註冊型別／seed三分類、尚無分頁；runtime固定埠剛停止可能有TIME_WAIT，占用拒絕而不停止他人。Vite preview非公開部署、viewport非真設備、來源私人證據／mock／DIRTY／品質限制保持。

### 下一步建議

重新fetch遠端main/read此交接與AGENTS/status/log/diff含staged/ff-only。下個Phase4B先作品管理CRUD：ADMIN可寫、VIEWER唯讀，draft與published一致、transaction＋expectedVersion／409衝突、稽核同交易、精確writer角色，schema用V4等新migration。先作品核心與版本/稽核一個里程碑，圖片上傳另拆。再package/build/production browser、HANDOFF、commit/push main與remote/clean；不改其他作品、不直接正式公開登入。

下方是歷史階段；「尚無登入」不是目前現況。

## 2026-10-12：Phase3 PostgreSQL／Flyway 保存（歷史）

起始實際remote main `86aab848cb91dee457cf592a0005d652abbc7913`，fetch→遠端HANDOFF→AGENTS/README→status branch log diff含staged→ff-only，乾淨main。使用者同意PG保存，AGENTS更新Phase3（只允許專屬PG資料容器，不開始app Docker部署）。套用cloud runtime/docker/network指引，revision267/current/enforced unrestricted，local socket/VFS、proxy及系統Java CA保持。只改developer-portfolio，JDK21/Maven唯讀使用，cache／env／logs在自己work；無模型或付費服務。沒有精確usage介面，獨立里程碑先收尾保存。

### 本次完成內容

- V1建立category／project／technology／project_technology／project_media／project_evidence與FK、唯一位置、狀態／JSONB約束、public排序index；V2凍結首次seed。5作品、3分類、32技術、48關聯、7媒體metadata、14證據；內容段落JSONB，其餘關聯化。
- PG是唯一查詢來源，舊catalog JSON移除；只修改平台自己的PG階段資料，四來源作品歷史證據保留。公開查詢published篩選、stored排序、prepared literal搜尋；列表分類/rows/count同一read-only RR snapshot。
- private scope helper＋PG17 Compose，bootstrap只初始化，Flyway owner與SELECT-only runtime reader分離。migration先檢查DB名稱才建立history，test-mode只接受portfolio_test；無baseline/clean/repair或重啟reseed。
- health真PG SELECT1／POSTGRESQL，PG不可用503 CATALOG_UNAVAILABLE，不退舊JSON／假零。runtime演練真API restart、PG stop/same-volume start、probe保存及production browser；沒有新增管理寫入或上传。

### 重要修改檔案

backend pom／application.yml／CatalogDatabaseGuard、ProjectCatalog、Controller／Errors、V1/V2 SQL、ProjectApiIntegrationTest；infra/compose.pg.yml、scripts/portfolio_db.py／run_persistence_runtime.py、tests/portfolio.spec.ts、AGENTS/.gitignore；README／PERSISTENCE／PUBLIC_API／PROJECT_PLAN／PROJECT_CATALOG／CLOUD_LOCAL_FOLLOWUP／PLATFORM_WORKFLOW／VERIFICATION／本交接与verification JSON。

### 測試／build結果

- 最終Maven **clean package 19項真PG／real HTTP測試全部通過，failure/error/skip0**；原12契約＋PG唯讀權限／DB變動不reseed／draft404／排序／FK位置／migration／錯DB前置拒絕。不是H2或mock repository。前端型別與production build成功。
- 最終clean JAR＋同dist，新的隔離portfolio_demo：**10項runtime核對＋13組browser全部通過**。fresh V1/V2、HTTP契約、owner temporary title/version可由API讀到；API restart保留七表fingerprint且不reseed；真PG stop使health及list503，same-volume start恢復原probe；只還原自己的probe後七表baseline相同。browser正常真PG/JAR，503／不完整／延遲UI仍明確stub，不混成相同故障證據。
- 使用19項clean產物再跑最後runtime，JARhash前後相同；V1/V2 source與packaged bytes相同，舊未使用JSON不在JAR，無fallback程式。[安全結果](verification/persistence-phase3-2026-10-12.json)另記來源／報告／migration／artifact hashes。
- 六個自己建立的test/runtime scopes全數container/volume/network0，所有自有JVM／preview退出且四個ports關閉。已人工看最後手機列表；private env/raw證據保留work，其他作品服務不動。
- 過程保存：第一次helper argparse把選項當REMAINDER，尚未建立scope，改明確--分隔command後成功。初次19與runtime10/browser13已過；再加test-mode避免fixture連demo後新19與runtime均過。最後artifact檢查發現Maven增量package仍帶舊JSON（程式未讀它），保留XML並clean package移除，再全套驗證。這是Maven build清理，不是DB/Flyway clean；沒有隱藏失敗或放寬契約。

### 尚未完成事項

Phase4管理身份／角色／權限與作品CRUD、版本／稽核、media上傳/物件管理；app Docker/gateway/TLS／CI、持久DB備份/離機/全服務restore、正式Demo、個人內容與其他作品截图。此輪僅媒體metadata，不算已完成圖片上傳。

### 已知問題

目前無分頁、API仍公開唯讀；分類前端保留四種註冊型別（seed實際三種），新增taxonomy要在管理階段同步契約。bootstrap僅自有scope初始與驗收，正式credential管理及migration獨立執行另驗。Docker工具Linux/WSL2 local socket、runtime腳本固定25487/19186/15186，占用會拒絕而不停止他人。Vite preview非公開部署；source證據私有權限与各作品mock／DIRTY／品質／設備限制仍保留。

### 下一步建議

重新fetch main/read本交接與AGENTS/status/log/diff含staged/ff-only。Phase4先做管理員登入與角色權限、再作品CRUD與版本/稽核，操作交易與授權一併驗證；由明確writer權限取代reader角色的寫入限制，不能用bootstrap/owner作管理API。已發布V1/V2不可改，schema新增V3等版本；seed不覆寫現有資料。不開始真金流、模型、其他作品改動或公開部署。各里程碑package/build/browser、HANDOFF、commit/push main及remote/clean確認。

下方是歷史階段；「資料只有固定JSON／尚無PG」不代表目前現況。實際cleanup/source/hash以本輪安全JSON為準。

## 2026-10-12：Phase2 公開作品 API 與 Vue 串接（本輪最新）

起始實際remote main `b4cc8af79431fdea3804325b1c822cf89d577c6a`；先fetch／遠端HANDOFF／AGENTS／README／status branch log diff含staged／ff-only，乾淨main。使用者同意上一轮建議的公開查詢API，AGENTS由Phase1更新為Phase2。只修改developer-portfolio；既有JDK21／Maven唯讀使用，依賴cache／設定／logs留自己的ignored work；沿用proxy與系統Java CA，無TLS繞過、付費服務、模型／App／其他作品服務啟動。無精確context/usage介面，單一里程碑收尾保存。

### 本次完成內容

- Java21／Spring Boot3.5.16提供GET作品列表／詳情／分類／health；搜尋、副標題、技術、分類／featured可組合。摘要與完整DTO分開、明確400／PROJECT_NOT_FOUND404、寫入405、no-store；health只證明固定catalog可用。
- 唯一作品資料移至backend catalog JSON；原前端projects.ts移除。四個來源作品內容與b4cc8af逐筆相同，僅平台自己的API／資料庫邊界更新。沒有PG、登入、RBAC、admin、migration或來源應用依賴。
- 首頁／列表／詳情改讀同源API，Vite dev/production preview代理自己loopback；無靜態fallback。載入、失敗重試、成功空態、不完整回覆拒絕；取消與序號阻擋過期結果，只有明確未知作品顯示404。
- Playwright自啟動package JAR與final dist，新增503／不完整／慢回覆驗證，保留版型與320/390px；新增API契約与啟動helper，更新範圍／文件。

### 重要修改檔案

backend/pom.xml、PortfolioApplication、catalog/Project/ProjectSummary/ProjectCatalog/ProjectController/CatalogProblem/CatalogErrors、application.yml、catalog/projects.json、ProjectApiIntegrationTest；src/api/projects.ts、composables/useRemote.ts、ProjectLoadState、三個views與卡片型別；vite.config、playwright.config、scripts/run-backend.mjs、tests/portfolio.spec.ts、AGENTS／README／PUBLIC_API／PROJECT_PLAN／CATALOG／CLOUD_LOCAL_FOLLOWUP／VERIFICATION／本交接及[安全彙總](verification/public-api-phase2-2026-10-12.json)。

### 測試／build結果

- 本輪完整Maven package：12個real HTTP/random-loopback Spring整合測試，failure/error/skip0；篩選、總量、摘要界限、中文／大小寫／trim、404／400／405、不改catalog与health，無mock controller或DB。
- Vue型別及production build成功。final package JAR＋dist、真Chromium151、自己的API19186／preview15186：13組全部通過，0skip/flaky/retry；首頁／搜尋分類／深連結／來源／圖片／手機／404與新增錯誤恢復、拒絕不完整、慢回覆不覆蓋。
- 503／不完整／延遲使用明確route stub，正常情境真JAR API，不能假稱真後端故障演練。手動查看本輪desktop/mobile截圖。source JSON與JAR內bytes相同，四個來源作品資料未變；hash見JSON。
- 失敗保留：首Maven因既有JDK bundled CA未信任proxy而PKIX拒絕，使用已配置系統Java truststore後package成功，未關TLS。首browser12pass/1fail因harness期待「查詢API」而实际文案「搜尋API」，修正精確文案並加強慢回覆完成等待後新報告13pass。最後JSON平台role/duration更新後已完整package12、build與browser13；失敗與成功logs/trace均保留work，不自動重跑掩蓋。

### 尚未完成事項

Phase3 PostgreSQL／Flyway、分類／技術／圖片關聯與資料保留；Phase4管理寫入／身份／權限／稽核；正式gateway/TLS／Docker／CI、公開Demo、個人經歷及三作品合成截圖。來源作品的歷史結果不重驗、不累加成平台測試。

### 已知問題

固定JSON啟動載入，修改需重建／重啟，沒有資料庫／分頁／管理API。目前分類型別固定四種、实际三種；前端需現代AbortSignal.any/timeout（本輪Chromium151，非真手機驗收）。Vite preview不是公開部署，正式同源/api與SPA fallback另做。GitHub私人證據權限与各作品mock／DIRTY／品質／裝置限制仍保留。查詢中的分類區隨成功資料更新，失敗無假零／靜態fallback。

### 下一步建議

重新fetch遠端main、讀此交接及AGENTS、status/log/diff含staged與ff-only後，做Phase3真正隔離PostgreSQL/Flyway。保留公開API與UI契約，設作品、技術／關聯、媒體／證據與順序，將這份固定catalog作明確seed；不可將其他作品業務／身份DB合併或自行baseline/clean。驗證restart保留、篩選／排序／404、migration及真PG整合，跑package/build/browser，再HANDOFF／commit/push main／remote與clean。管理寫入與TLS各自後續里程碑。

下方是歷史階段；其中「平台只有前端／API待做」已由本輪更新。

## 2026-10-12：作品入口與證據更新（本輪最新）

從實際遠端main `4d2eec1a8d9c37579aa258aa055a776737af7031`接續。先fetch／遠端HANDOFF／AGENTS及README／status branch log diff含staged／ff-only，保持Phase1前端與原版型。使用者明確授權捨棄ELPSY舊草稿並開始平台；先精確清除shop_bak_ap的13修改＋5新增、Shop_back的6修改＋4新增，未清work/env/data/依賴，兩者ff到遠端339051d／910af78且乾淨。未產生Shop新commit或修改遠端產品。

### 本次完成內容

- 首頁精選發呆亭、ELPSY Shop（含獨立Flutter App）、Encore、Atlas；列表再含平台共五條目，不代表最終數量上限。日日選物暫緩，RBAC/Python占位移出，沒有刪除獨立倉庫。舊slug返回404。
- 各作品的實作／歷史驗證／限制／下一步及14個固定版本文件外連回填；私人GitHub權限與無公開Demo明示。不將Cloud收尾標成商用Completed。
- Atlas更新真實免費Qwen3/BGE-M3/Qdrant/RRF/重排/權限/品質，複製兩張已提交第四里程碑截圖。Encore8/12後DIRTY、ELPSY原APK與CI交付差異、發呆亭mock和保留試算限制均保留。
- 搜尋納入副標題，保持既有版型，裝飾封面不再写已提供內容待確認。新增production瀏覽器回歸、來源盤點、本機待補清單，修正平台共同盤點過期倉庫對應。

### 重要修改檔案

src/data/projects.ts、types/project.ts、views/HomeView/ProjectsView/ProjectDetailView、components/ProjectLinks/ProjectVisual、styles.css；兩張public Atlas PNG；package/lock、playwright.config.ts、tests/portfolio.spec.ts、.gitignore(work/)；README、PROJECT_CATALOG、CLOUD_LOCAL_FOLLOWUP、PLATFORM_WORKFLOW、PROJECT_PLAN、VERIFICATION及本交接／verification JSON。

### 測試／build結果

本輪平台`npm ci --ignore-scripts`、vue-tsc／Vite production build成功。真Chromium151／final dist loopback preview，9組回歸通過、0skip/flaky/retry；包含四作品證據／限制、搜尋分類、深連結reload、圖片載入、320/390px八頁與404。14個固定來源文件存在、2圖bytes與來源相同；source／artifact hashes見[JSON](verification/project-refresh-2026-10-12.json)。人工看桌面／手機截圖。各作品189／260／629／45等數字都是來源歷史結果，沒有重跑那些服務或模型。

失敗完整保留：首輪15173占用，改空閒15186而非停止其他服務；第二輪4pass/5fail是測試漏h1裝飾句點及lazy-load圖片未scroll，修正harness後第三輪9pass。沒有自動重試或放寬功能驗收。最後npm ci後重建同名JS/CSS；原報告、trace、review clone在ignored work，不含新的模型／SDK/APK下載。

### 尚未完成事項

平台Spring Boot API、PostgreSQL／Flyway、管理後台與媒體治理、CI／正式部署仍未開始。其他三作品去識別截圖、個人真實經歷與公開可讀source／Demo待補。本機/設備/帳號與正式服務按CLOUD_LOCAL_FOLLOWUP逐項驗收，不本輪下載到使用者電腦。

### 已知問題

私人倉庫訪客未必可讀；歷史證據不等於最新全套再驗。Encore持續預扣仍部分完成，App真機／iOS及黑畫面未結案，Atlas語意品質限制保留。產品目前仍In Progress、平台靜態資料；有些封面為裝飾。現有埠15173服務不屬本輪，保持原狀；測試preview15186已停止。無精確context/usage工具，按獨立里程碑收尾保存。

### 下一步建議

重新fetch遠端main／讀本交接與AGENTS／核對Git後，下一個平台里程碑做Phase2公開作品列表／詳情／分類API，保留現有搜尋、深連結與錯誤/空狀態；範圍更新要依新的使用者任務與原計畫處理。Phase3再做真正隔離PG保存、Phase4管理。不混用作品DB或身分，不先追加真服務／HA等完成条件。每里程碑tests/build、HANDOFF、commit/push main與remote/clean核對。

下方保留歷史交接；其中「ELPSY来源待確認／搶票尚未建立／Atlas只有baseline」已由本輪來源盤點更新。

## 2026-10-06：工作階段與 context 安全（本輪最新交接）

起始基準為遠端 main `c6787b0`；只更新規範與交接，沒有商業程式或 migration 變更。

### 本次完成內容

- 四個 workspace 倉庫的 AGENTS 與平台文件加入一致的八項規則：主動檢查可用 context／token／usage、預留收尾空間、額度可能不足時停止新增大型修改、優先測試／build 及交接／commit／push、每個里程碑立即保存、compaction 後重建實際狀態、最後核對乾淨及遠端 main。
- 沒有精確用量介面時採保守分段，不捏造剩餘數字；規則適用整個展示平台及後續 Web／後端／App／搶票專案。

### 重要修改檔案

[AGENTS.md](../AGENTS.md)、本交接、[驗證摘要](verification/context-safety-2026-10-06.json)、[平台共用規範](PLATFORM_WORKFLOW.md)、[驗證文件](VERIFICATION.md)；全文見 [平台規範](PLATFORM_WORKFLOW.md)。

### 測試／build 結果

- 本輪前端型別檢查與 production build 成功（npm run build）。
- 此專案只有前端，沒有後端測試或建置。
- 八項要求完整，四份 AGENTS 與平台文件內容一致；交接六項、新增連結及 git diff --check 核對通過。原始日誌留在 workspace 暫存，Git 僅收錄非敏感摘要與雜湊。
- 瀏覽器／外部 HTTPS／真實模型品質未重跑，前輪數字維持歷史條件。

### 尚未完成事項

- 原功能與本機／設備／帳號待補工作維持前輪紀錄；本輪沒有新增功能。
- ELPSY Shop 正式倉庫來源待確認，新搶票倉庫尚未建立；規範適用，但不宣稱已修改那些倉庫。

### 已知問題

- 目前沒有可用的精確剩餘 context／帳戶 usage 查詢工具；保守保留收尾空間，不宣稱已讀取實際剩餘值。
- 測試／build／push 失敗時如實記錄並保留安全成果，不標為成功、不 force push；各作品既有功能限制仍見前輪紀錄。

### 下一步建議

1. 新階段重新核對遠端 main／交接／Git 狀態；每個可驗證里程碑完成就保存，避免累積到大型任務最後。
2. 確認 ELPSY Shop 正式倉庫來源；後續依實測更新展示案例，新 App／搶票倉庫初始化時納入相同安全規則。
3. 發生 compaction 或資訊不明時，重讀原始檔、status、diff（含 staged）、log、HANDOFF 與遠端後才續作；收尾確認 status 乾淨、main 已包含最新 commit。

## 2026-10-06：平台共同流程（上一輪紀錄）

起始遠端 main：`fec349a`；本輪驗證的程式基準：`fec349a`。完成後由各倉庫 main 提交並推送；確切提交以遠端 main／git log 為準。平台規範見 [共同工作流程](PLATFORM_WORKFLOW.md)。

### 本次完成內容

- 使用者指定的九步流程適用於整個平台，各倉庫各自同步 main、讀交接、核對狀態、測試／build、更新交接、commit／push；新 Web／後端／App 與搶票作品亦適用。
- 建立平台共同規範、各倉庫清單與六項交接模板。維持 Phase 1 前端與既有畫面，未開始 Phase 2 或新增後端。
- 一般單一作品工作仍限於該作品；本輪需求明確涉及平台流程，才同步更新四個 workspace 倉庫。未修改其他 GitHub 倉庫、寄信、執行金流或下載模型。

### 重要修改檔案

[AGENTS.md](../AGENTS.md)、[README.md](../README.md)、`docs/HANDOFF.md`、[驗證紀錄](verification/platform-workflow-2026-10-06.json)、[PLATFORM_WORKFLOW.md](PLATFORM_WORKFLOW.md)、[VERIFICATION.md](VERIFICATION.md)。

### 測試／build 結果

- **本輪實測**：前端型別檢查與 production build（`npm run build`）成功。
- 此作品只有前端，沒有後端測試或建置；未為文件變更新增與實作重複的測試。
- 交接六項內容、共用規範與 `git diff --check` 已核對；原始日誌留在 workspace 暫存，Git 僅收錄不含帳密的摘要與雜湊。
- 舊功能、瀏覽器／HTTPS／真實模型與效能數字保留為歷史條件，本輪未重跑或改寫其結論。

### 尚未完成事項

- 展示資料仍含第一里程碑內容；之後依各作品最新實測更新案例。ELPSY Shop 的正式維護倉庫待使用者回覆，新搶票倉庫尚未建立，App 工具鏈／真機與部署仍待補。
- 平台流程對 ELPSY Shop／App／新搶票專案同樣有效；缺少正式倉庫對應或尚未建立的項目不宣稱已修改。

### 已知問題

- e-commerce-platform 是日日選物工程案例，不能當作 ELPSY Shop。GitHub 存在兩組 Shop 候選，其中 ELPSY_Shop-AP／WEB 為 master；本輪未修改它們或更改預設分支。作品集連結與 build 不代表已公開部署。
- 分支保護或檢查失敗時記錄真實阻礙，不 force push；既有 master 倉庫先盤點 main／部署依賴，不自行改名。

### 下一步建議

1. 重新 fetch 本倉庫遠端 main，先讀該版本本文件，再核對 status、branch 與近期 commit；正常後 fast-forward 同步再修改。
2. 先確認 ELPSY Shop 正式後端／Web／App 來源與 main 歷史；對應倉庫接手時套用同一流程。再依各作品已驗證成果更新展示案例；新搶票倉庫從初始化就加入 AGENTS／交接。
3. 階段完成後跑相應測試與 production build，成功後更新六項交接，commit／push main，再確認遠端一致與沒有重要未提交修改。

## 先前里程碑紀錄（歷史）

更新日期：2026-10-05（Asia/Taipei）。先讀根目錄 `AGENTS.md`、[PROJECT_PLAN](PROJECT_PLAN.md)、[README](../README.md)、[VERIFICATION](VERIFICATION.md)。

## 目前成果與接續範圍

- Phase 1 Vue 3 / TypeScript / Vite / Tailwind CSS / Vue Router 前端 MVP；Home、Projects、Project Detail、About、404、搜尋、分類與響應式布局。
- 求職定位 Java Full Stack / Backend Engineer；不展示 GYMLIFE。
- 五個靜態 mock 項目：Developer Portfolio、E-Commerce Platform、RBAC / Authorization Demo、AI Knowledge Assistant、Python Utilities。
- 電商與 Atlas 已回填實際第一里程碑成果、截圖與限制；RBAC / Python 保持 Planned。
- 作品集本身沒有後端、資料庫或模型 API；個人經歷與正式公開 Demo 待補。

本次只準備 GitHub / 雲端接續，**不要開始作品集 Phase 2**。後續只依各專案已驗證成果更新展示內容，保留現有視覺設計與版型。mock 資料在 `src/data/projects.ts`。

獨立作品倉庫：

- https://github.com/Elpsy-Deng/e-commerce-platform
- https://github.com/Elpsy-Deng/ai-knowledge-assistant

私人倉庫需有 GitHub 存取權；連結不表示公開可讀或已部署。其他專案程式碼不在本倉庫。

## 雲端 / Linux

使用 Node.js 24 與 npm，從實際 checkout 根目錄執行，不使用 Windows `D:\Project` 路徑：

```sh
npm ci
npm run build
npm run preview -- --host 127.0.0.1 --port 4173 --strictPort
```

build 包含型別檢查。Linux / 雲端指令尚未在雲端实跑；preview 僅供工作區驗證，未公開部署。

## Git 往返

接續前確認分支與工作樹，保存未提交變更後同步 GitHub。重要成果提交並推送；需要合併時用 PR。回到 `D:\Project\developer-portfolio` 開發時拉取相同分支。新對話先讀本文件；對話不取代原始碼與文件。

`node_modules/`、`dist/`、環境檔與日誌不納入 Git。

## GitHub 保存前驗證（2026-10-05）

本機 `npm.cmd run build` 成功，包含 Vue / TypeScript 型別檢查與 production build。提交清單未包含依賴、build、環境檔或本機資料。此次沒有修改應用程式功能或進入 Phase 2；雲端环境尚未建立及驗證。
