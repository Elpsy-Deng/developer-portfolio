# 展示平台驗證紀錄

## 2026-10-13：Phase5D HTTPS／可信gateway／匿名Session（最新）

[操作](HTTPS.md)／[安全JSON](verification/https-phase5d-2026-10-13.json)。本輪只改本repo，V1–V9／schema保持。

- 兩輪完整Maven clean package **114 tests／11 suites／failure-error-skip0**，新增5真PG Session＋4 peer單元，原105回歸；Vue typecheck/build、標準Dockerbuild、23 Python guards（7/8/4/4）通過。
- 本地三次容器嘗試受VFS ENOSPC阻擋（CI1 migration、CI2 runtime mkdir、runtime3 migration/finally log），不能標local full PASS或TLS完成。保留raw/FAIL摘要，只刪自有unused images及逐ID/time/description cache、child-before-parent回收；未刪其他repo資源/SDK或global prune。自己的containers/volumes/networks0，host private檔保持ignored work。
- ENOSPC cleanup新增已驗證CI-own scope的無log idempotent fallback：只fault-inject log建立失敗，真Docker down/purge／resource0實測，沒有放寬scope或auth/CSRF/quota。
- **本輪Hosted [38386662847](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847) SUCCESS**，精確576bcf90b053f7aa5aaeaf8537fbe931d195a4a5、官方預設pinnedPG／標準app-gateway映像，115／11 suites、27／43、17／5、30真TLS／CA-SAN驗證ENABLED／cleanup0；實際下載唯一701-byte ZIP且只有summary.json。SHA／全部數值在安全JSON，不是借舊114或前輪結果。公開主機/domain/DNS尚無資料，沒有TLS browser、多外網client容量或ACME續期驗收。

## 2026-10-13：Phase5C 展示內容（最新）

[操作](SHOWCASE_CONTENT.md)／[安全JSON](verification/showcase-phase5c-2026-10-13.json)／[平台頁截圖與fixture條件](screenshots/showcase-2026-10-13/README.md)。起始a58a634，只改本平台，來源repo唯讀；不改Java／schema／不可變V1–V9。

- 五作品各三個來源核實工程案例、五張Graphviz架構說明PNG與固定SHA證據。ADMIN API先plan／審閱hash再apply，保留人工字段／發布排序／原media，跨作品不是原子交易；中途故障journal標部分／不確定，不盲重送。media no-op以metadata辨識，不是pixel來源鑑定。
- 本輪本地完整新run **18 Python guards、Maven clean package105／9 suites、Vue typecheck／production build、標準Dockerbuild、原27容器／43browser、新增17真API／4showcase browser全過**，failure/error/skip/flaky0。新驗收包含全targets stale preflight、VIEWER拒絕、人工role保留、原media／audit／發布／排序保持、5張真上傳PNG、重放拒絕／新plan no-op與API restart保存。
- 初次105／27／43及15 showcase API前置過，showcase browser3過1失敗：圖標修正在初次dist freeze之後，當輪正確拒絕ACTUAL SCREENSHOT。保留FAIL，在新output重build最終dist／映像後重新全流程通過，不借成功數／skip／放寬assertion。
- SQL9／public12 Git/source/packaged bytes保持，固定JAR/dist摘要見JSON。自己兩scope cleanup PASS／各resource0；原失敗與成功raw、私有帳密／plan／journal／media留ignored work。Cloud單層PG17.11 override，Hosted預設官方PG的結果獨立記錄。
- 保存五張真平台頁390px合成fixture截圖，不是其他來源應用操作證據。Atlas歷史圖保留；發呆亭／ELPSY／Encore產品截圖仍待。個人資料依使用者選擇暫不公開，不編造職歷／聯絡方式。
- **本版本Hosted [38356320602](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38356320602) SUCCESS**，精確commit685258743ac21f0c7c71f23f166206bc521ba57e、所有步驟成功，預設官方pinnedPG。實際下载唯一626-byte ZIP且只有summary.json，核對105／27／43／17／4、零failure/error/skip/flaky、cleanup资源0、SQL9/public12；完整摘要在安全JSON。Hosted/local distTree一致，JAR只驗各run固定，非跨package bitwise聲明。未跑完整48 backup restore、8h soak、公開HTTPS或真provider／設備／模型，歷史與本輪範圍分開。


## 2026-10-13：Phase5B CI（最新）

[操作](CI.md)／[安全JSON](verification/ci-phase5b-2026-10-13.json)。起始2d0020c，只改展示平台CI／工具／文件，無Java／Vue業務或migration變更。

- actionlint1.7.12語法通過、五個actions v4 commit SHA已由官方repo tag查核。新6個evidence guards＋原7backup artifact guards通過、Pythoncompile通過。
- 新自己的PG／private output，**本地同一run_ci入口全過：Maven clean package105／9 suites（error/failure/skip0）、Vue typecheck／production build、標準Dockerfilebuild、27容器checks與43 production Chromium browser（retry/flaky/skip0）**。不是援用Phase5A結果；本輪重新執行。
- V1–V9 Git/source/JAR、12public Git/source/dist固定；JAR/dist在acceptance不變，安全JSON保存摘要hash。always cleanup及額外idempotent cleanup實測；两scope容器/volume/network0。raw/image/media/env/trace只留private ignored work。Cloud PG17.11既有單層base override，Hosted workflow不指定override。
- 第一完整流程105/build/Docker通過但VFS ENOSPC使media建立失敗，schema assertion未讓其算成功；summary FAIL/cleanup PASS。保存失敗、仅刪先前本專案自有unused verification images与逐ID/時間辨識快取，第二新output全流程PASS；無global prune/權限放寬/skip/自動retry。
- **GitHub Hosted首輪[38351233282](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38351233282) SUCCESS**，精確commit54e2ad20d970e22a7228f7c0ae0eca30b93319e3；所有validation/cleanup/upload步驟成功。实际唯一588-byte ZIP只含summary.json，下載核對同commit與105/27/43、cleanup0、9 migrations/12 public。使用預設官方pinnedPG，無Cloud override；安全JSON保存Hosted摘要。local/Hosted JAR hash可隨package metadata不同，均核對run內固定與source SQL；distTree一致。admin設定API403未影響CI，沒有改policy/billing/branch protection。

## 2026-10-13：Phase5A 私人同源容器與相容 app 回退（最新）

[安全JSON](verification/deployment-phase5a-2026-10-13.json)；[操作與限制](DEPLOYMENT.md)。起始 remote main 7b155df，只改展示平台。

- 本輪 **Maven clean package 105 tests／9 suites 全過**，failure/error/skip0；新增6包含真PG history replay、future/checksum拒絕與privileged config／role guards。原99重新回歸；Vue typecheck／production build、7 Python backup artifact guards及新操作腳本compile通過。
- 固定JAR/dist、由最終標準app/gateway Dockerfiles建立的多層映像：**27真容器檢查＋43 production Chromium browser全過**，exit0、retry/flaky/skip0。Cloud PG17.11用既有單層base override、新自己的volume和credential；本輪未跑預設官方PG image。測試不關auth／CSRF／quota。
- 未migration API以真Flyway驗schema失敗且0表；獨立migration安装V1–V9後退出。API非root／read-only／dropALL／無host API port；inspect確認只掛runtime secret＋自己media、無owner/bootstrap秘密。真Nginx的SPA深連結／圖片和missing assets404；`/projects`與有實體素材目錄的Atlas路由由完整browser驗過。
- 真ADMIN／VIEWER、缺CSRF403、draft/publish/stale409、multipart normalized PNG；API重啟保留原Session／圖，API停機502/504而static200；PG停機503無fallback，same-volume恢復Session/domain/image。migration replay保持完整history與編輯資料。
- 壞候選（同一JAR、完整Flyway URL但enabled=true）由實際deploy guard拒絕，沒有DB mutation。回退前一固定app image只validate，不migration／schema downgrade；原history／Session／圖片bytes保持。
- V1–V9 git/source/JAR與原12 public git/source/dist bytes完全相同，JAR/dist驗收前後固定。已檢視真gateway 390px合成封面／表單圖。兩自有scope containers/volumes/networks0，25486／25490／15190拒絕連線；host合成media/credential移存private驗收目錄、deployment media空。所有raw/env/trace/失敗輸出留ignored work，不提交。
- 失敗保留：兩次Java visibility compile；第一容器JAR600/root不可讀（該輪弱schema assertion無效）；兩輪VFS ENOSPC migration；gateway loopback wget被Cloud代理403，改`-Y off`；首browser28/43，實體目錄轉址帶內部埠與缺PNG fixture，修relative redirects／SPA file-only fallback與fixture。修gateway探索曾遇FROM本地ID語法／非root chmod錯誤；一次release缺失preflight不算驗收。次browser43/43及24checks，但壞候選被未解析URL提前拒絕，未算guard／rollback成功；完整合成URL後最終標準映像27/43全過。僅刪本輪未用自建images與逐ID辨識快取，沒有global prune、既有migration修改、repair、權限放寬或自動重試。
- 界線：loopback HTTP（Secure僅explicit opt-in關閉）、無公開TLS／可信forwarding／CI，gateway peer共用quota；僅同V9相容app回退，不是跨schema恢復／災難切換。43UI部分503/429/expiry仍是explicit stubs，真PG/API停機另驗。Phase4H完整48backup restore屬歷史，本輪只重跑7artifact guards；未假稱重新完成48或8h soak。

## 2026-10-13：Phase4H 協調backup／隔離restore（最新）

[安全JSON](verification/backup-restore-phase4h-2026-10-13.json)，[操作與限制](BACKUP_RESTORE.md)；起始b23baf0，只改平台操作工具／文件、無新schema/seed。

- 本輪 **7 Python防護tests／99真PG17.11 real HTTP Maven clean package／Vue typecheck production build全過**。**48真backup/restore＋74 packaged runtime＋43 production Chromium browser全過**，0retry/flaky/skip。99/74/43是重新回歸原功能，新7/48是備份／還原驗收。
- Offline NOLOGIN協調＋before/after table/grant/file指紋；3 managed PNG＋1 old orphan、59000-byte合成dump／4 objects。測running API/active DB client拒絕且不terminate、source旗標fail recovery、缺圖／symlink、模拟journal恢复、不可覆寫source、corrupt artifact preflight、invalid archive真PG拒絕/quarantine。
- 新scope restore精確保留內容/versions/三audit/hash/quota/history/table-column-schema-default-DB grants及role限制/media bytes；兩Session表空，舊Cookie401、原password重登、disabled401/VIEWER403、draft404/public圖200、ref409；writer version5/audit／normal GC retired1＋old orphan1／same-volume PG-JAR restart有效，source tables/files独立及原Session可用。drill关闭scheduled GC只为先核对byte，auth/CSRF仍开启；endpoint GC另验。
- 第一drill29前置checks過，但public schema默认PUBLIC USAGE在drop/recreate後缺失，grants fingerprint挡下；保留标准schema/reviewed TOC＋去除helper table默认reader grant再restore ACL，第二48过。第一PG up health看见init临时服务导致readiness查询失敗，没有启动Maven/写role；补final TCP/SQL bounded probe后up/package成功。所有失败raw/result保留private work，不skip grants、repair、reset、自动retry。
- V1–V9 git/source/JAR保持、原12 public bytes保持、JAR在drill/runtime固定，source/dist/report hashes已留。unit fixture账号/audit/image/upgrade schemas0；八自有scope容器/volume/network/fixture media0、自己process退出及六ports TCP拒绝。raw backup/dump/session/credential/trace不提交，backup合成副本私有留work。已看390px原尺寸crop，前端未改。
- 限制：private同磁盘manual offline backup，非加密/异地/PITR/正式RPO-RTO；可信owner能绕过冻结，hash不是来源签章；只支持stock helper PG17及匹配migration，不包含Git static／JAR／env。突断access journal须恢复核对；正式部署／本机验收另做。原UI一般503/429/到期是explicit stubs；真DB故障在Java/runtime，quota/8h为自己的时间fixture而非soak。

## 2026-10-13：Phase4G 分類／技術治理與分頁（最新）

[安全JSON](verification/taxonomy-pagination-phase4g-2026-10-13.json)；完整契約：[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。起始ea18798，只改平台。

- **本輪實測：99真PG17.11／real HTTP tests、Maven clean package、Vue typecheck／production build通過**。原87＋新12，包括引用含草稿、改名父version／snapshot、no-op/sort-only、真並行、CSRF/roles/精確grants、兩種audit故障rollback、implicit技術、容量500／引用101改名拒絕、分頁count/items／literal filters／非法bounds／next wrap。
- **74 runtime／43 production Chromium browser全過**，0retry/flaky/skip。fixed JAR／final dist；真字典／父version／audit跨JAR及same-volume PG restart保持，delete audit保留、末頁count不丟，原身份／密碼／quota／圖片／PG停機503及恢復回歸。4新browser包括動態分类、雙editor與父editor409、ref409、技術生命週期/503保留、size2深連結/慢管理回應、VIEWER真403／390px；一般故障UI為明確stubs，不能稱真DB outage。
- V1–V8 git/source/JAR同，V9 source/JAR同；原12 public bytes保持。兩V2→V9／兩V5→V9真升級保留既有資料／身份／Session/media後移除schema。390px頁與原尺寸editor/audit裁切已檢視。
- 單元fixture accounts／三種audit／image／upgrade schema0；3分類／32tech／5作品保持。两自有scopes containers/volumes/networks/media files0、ownProcessesStopped及四ports TCP拒絕；沒有停其他服務。原bootstrap hash/role保持；合成runtime/audit在自己volume purge。private raw log/XML/trace/env不提交。
- 第一package98 tests中6 failures，合成分類prefix超40，被400 guard拒絕；修正fixture而非放寬產品，capacity新增後完整99過，raw失败保留；兩frontend build均過，runtime一次74/43過。無Flyway repair／既有migration checksum改動／帳密reset。
- 限制：各request snapshot、不保证跨頁穩定snapshot；offset非大型目錄效能證據。字典500／rename100、序列化後台；管理GET列表由array改envelope須client一起升級。沒有完整audit导出／批次排序／免費忘記密碼交付／正式部署／協調backup/restore。quota／absolute expiry用自己的時間fixture，不是8h soak。

## 2026-10-13：Phase4F 本人密碼更改（最新）

基準7e37f3f。[SELF_PASSWORD_CHANGE](SELF_PASSWORD_CHANGE.md)／[安全JSON](verification/self-password-phase4f-2026-10-13.json)。只驗平台。

- 最終clean package **87真PG17.11／real HTTP全通過，failure/error/skip0**；Vue型別＋production build通過。新password10覆蓋兩角色只本人、wrong/reuse/invalid/stale、CSRF、持久account/peer/global與login分離、配額登入不重設/到期、並行單winner／單audit、audit故障hash/Sessionrollback、stale role/disable、credentials最小grants／安全回應。
- 固定JAR/dist、全新PG、Chromium151：**65 runtime＋39 browser全過，PASS/exit0、0retry/flaky/skip**。真password更改後old401/new登入v2、hash/audit/version/新Session跨JAR/PG重啟保留、耗盡passwordquota不清零；真PG503及四pool readiness。原身份/CRUD/media/login/absolute回歸保持。
- 4新UI：VIEWER本人改密碼、第二context撤銷/旧拒新登入、wrong/reuse/兩次不一致清全部秘密且身份不變、stub429倒數/不重送、stub503結果未確認/390px。正常更改走真PG；UI429/503与原匿名expiry明確stub，真故障/expiry另驗，不混證據。owner只改disposable時計fixture，非8h soak。
- V1–V7 git/source/JAR同、V8 source/JAR同、原12個public bytes保持；兩V2→V8與兩V5→V8真升級保留資料後drop schemas。已看390px三空password畫面。三個自有scope containers/volumes/networks/media0、ownProcessesStopped與四ports TCP拒絕；raw secrets/env/trace/XML只留private work。
- 首helper錯用uppercase身份env，up拒絕後仍起Maven、V8缺role造成context初始化27 errors；保存失敗raw log/XML，修正credential keys、新scope先驗up exit0再package87通過。沒有repair/checksum/reset舊DB，也無automatic retry；最終runtime一次65/39通過。完整配置scope重跑enable-credentials維持env bytes不重設。
- 忘記密碼reset/交付/Email、正式TLS/proxy、匿名Session端點節流、容量與協調backup/restore仍待；不把本人更改標成完整身份或正式營運驗收。

## 2026-10-13：Phase4E 帳號治理／最後 ADMIN 保護（最新）

基準3fdfbe8。[ADMIN_ACCOUNTS](ADMIN_ACCOUNTS.md)／[安全JSON](verification/account-governance-phase4e-2026-10-13.json)。只驗平台，沒有來源作品測試累加。

- Maven clean package **77真PG17.11／real HTTP全過，0failure/error/skip**，前端vue-tsc／production build成功。新identity10：真建立登入/secret-free response與audit、CSRF/viewer/匿名、stale/duplicate/invalid/弱密碼、最後ADMIN/no-op、兩個並行self降權200+409、role/disable/reenable/多Session/self不重存、stale actor交易內拒絕、audit故障create/update/Session刪除rollback、最小role/DB secret key whitelist。
- 固定JAR/dist／新PG／Chromium151：**56 runtime＋35 browser全過**，PASS/exit0、0retry/flaky/skip。治理version/hash/identity audit經JAR及same-volume PG restart保存；真停用撤銷登入、PG停機503、identity/auth/public獨立恢復ready，原CRUD/media/登入配額/absolute期限回歸全過。原bootstrap兩帳號hash/role保持，browser新合成帳號与identity audit為授權fixture，直到自己volume purge。
- 4個新UI回歸：建立/停用/啟用/安全稽核、兩頁409、唯一root與VIEWER拒絕、stub503清秘密保留代號/恢復/390px。一般503/原429/匿名expiry为明確stub，DB audit故障與真expiry由Java/runtime另證；fixture時間調整非8h wall-clock驗收。
- V1–V6 git/source/JAR同，V7 source/JAR同，原12個public bytes保持；兩V2→V7與兩V5→V7隔離schema保留已有資料後移除。兩個自有scopes containers/volumes/networks及media files全0、ownProcessesStopped、四ports TCP拒絕；test帳號／兩種audit／image／upgrade schema0，raw秘密與trace留work。
- 完整configured scope重跑enable-identity保持env bytes，不重設role/帳密。已看390px完整頁與清空初始密碼。此輪沒有test/build/runtime失敗、沒有automatic retry；最後frontend細部訊息修正後重建，再固定產物測試，未在runtime中重建。
- 密碼更改/reset/交付与正式HTTPS/proxy、匿名Session節流、實際容量與DB+images協調restore仍未完成；API100 count guard、recent50 audit不當作容量/完整身份產品驗收。

## 2026-10-13：Phase4D 持久登入限流／絕對 Session 期限（最新）

基準69bfa49。[LOGIN_SAFETY](LOGIN_SAFETY.md)／[安全報告](verification/login-safety-phase4d-2026-10-13.json)。只驗平台，免費PG，不串接其他作品或身份provider。

- 最終Maven clean package **67項真PG17.11／real HTTP全過，failure/error/skip0**（原56＋登入安全10＋V5升級保留1）。Vue typecheck與production build通過。
- 原子account/peer/global配額、容量/過期清理、真並行最後slot、fake轉送header／Cookie／帳號無繞過、錯誤泛化/Retry-After、auth限流讀取故障503、最小DB權限；活動不續期、舊marker缺失／未來時間／到期先拒寫入與舊Cookie撤銷／新密碼認證均有實證。真V5→V6兩schema保留account/session opaque bytes、media、四來源與已編輯內容；V2→最新兩schema回歸保持。
- 最終固定JAR/dist、全新自己PG、Chromium151：**50 runtime＋31 browser全部通過**，0retry/flaky/skip、PASS／process exit0，JARhash前後相同。PG/JAR restart保留login-time／耗盡quota，真PG停機503，auth/public分別有界readiness恢復、真absolute expiry/重登；CRUD/media/DB原回歸全過。
- 時間用owner只調disposable window epoch/Java Long，不是5分鐘/8小時wall-clock soak；quota runtime另用更嚴3/5/8，browser是default30/60/120不關閉限流。UI429/匿名到期與原503等是明確route stub，不混稱真backend故障；原始Cookie/hash/env/trace留work。
- V1–V5 git/source/JAR完全同，V6 source/JAR同，原12個public檔bytes不變；最終390px倒數截圖已查看。五個本輪自有scope containers/volumes/networks0、media objects/incoming0、ownProcessesStopped、四ports TCP拒絕。test fixture帳號/audit/images/upgrade schema0；7個暫時quota rows隨disposable volume purge，不是app啟動清零。
- 首66/47/31全過後更新未發布V6平台自介及新增V5 preservation，fresh scope67過。下一候選browser31過但quota-phase PG restart只等public health、auth pool短期503使整run FAIL/exit1（42個checks）；補35秒read-only auth readiness、記每次status、不重送登入/write/assertion；重跑前bare bind又遇TIME_WAIT，沒有scope或listener，補SO_REUSEADDR並實測仍拒live listener98。相同JAR/dist、新scope最後50/31與exit0；所有舊結果/log保留，無automatic test retry。
- 不含帳號治理／密碼重設、可信proxy／formalHTTPS／匿名Session端點節流、正式容量與coordinated backup/restore。代理共用peer、fixed-window burst、hash字典推測及401非完整timing等價限制見LOGIN_SAFETY，不能宣稱正式營運安全驗收。

## 2026-10-12：Phase4C 圖片管理與安全回收（最新）

基準8a893b8。[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)／[安全報告](verification/media-phase4c-2026-10-12.json)。本輪只驗平台。

- Maven clean package **56項**真PG17.11／real HTTP全過，failure/error/skip0（既有44＋media12）；前端vue-tsc與production build成功。
- PNG/JPEG實際decode／尺寸／重編碼、5MiB413、fake／SVG／MIME不符拒絕；draft私有、published讀取、no-store/nosniff、SHA/bytes、版本／CSRF／viewer、metadata／cover替換，audit/IO失敗rollback，退役GC重試／24h grace／symlink均有實證。
- 固定JAR／dist、全新PG／私有root、Chromium151：**31 runtime＋29 browser全過**、0retry/flaky/skip、outcome PASS／exit0。JAR及same-volume PG restart保存完全相同bytes與media版本；真PG stop不繞過發布檢查送圖；刪除後404、回收檔案、audit保存。UI storage503為明確stub；偽造PNG及正常流程真走後端。
- V1–V4原bytes不變、V5 source/JAR一致、原public/static素材bytes不變。三個自有scope container/volume/network0，objects/incoming皆0，自有程序與四ports關閉；最終390px真圖截圖已查看。Private env/raw evidence留work。
- 初次44中1fail為upgrade assertion仍期待舊status文字，改驗V5真實圖片里程碑後新56通過。原runtime31/browser29通過後發現fixture依賴work，改成自生同bytes合成圖，新scope固定產物重驗31/29；不重建JAR/dist、不overwrite失敗log。驗收工具現在明確PASS/FAIL，避免只看checks數當成功。
- 本機adapter已實作；SeaweedFS、正式HTTPS／身份限流／帳號治理／coordinated DB+images restore尚未完成，不宣稱正式對外營運安全驗收。

## 2026-10-12：Phase4B 作品管理／版本／稽核（歷史）

基準7f1d30b。[ADMIN_PROJECTS](ADMIN_PROJECTS.md)／[安全報告](verification/project-management-phase4b-2026-10-12.json)。只驗平台，來源作品數字不重驗或累加。

- 最終Maven clean package **44**項真PG17.11／real HTTP全部通過、0failure/error/skip（公開／升級20、登入授權11、管理13）。前端型別與production build成功。
- 新功能實證：完整draft→publish→unpublish→delete、稽核留存、刪除slug不重用、排序耗盡409、既有media/localDemo保留；兩個同時HTTP editor只有200/409、單一update稽核。稽核INSERT權限故障使create／update／delete全交易rollback，包含tech與child關聯。
- writer不能讀hash/Session、改身份／分類／schema／audit；audit只能append，reader/auth看不到它。兩份真V1/V2→V3/V4升級驗证保留已編輯平台及四來源作品，並再次migration0。V1/V2/V3 bytes不變，V4 source/JAR一致。
- 最終固定JAR／dist、全新PG：**24 runtime＋24 browser全過**，0retry/flaky/skip、process exit0，JARhash前後一致。真JAR/PG restart保存CRUD version／audit／登入，真PG停機503；一般UI503仍stub，與真故障分開。三個自有scope container/volume/network0，自有四ports無listener且TCP拒絕；最終390px編輯截圖已人工查看。
- 初次public projection拆出後漏WHERE/AND空白，回歸9fail/1error，修正後44全過；Vue required欄位union型別修正，未關typecheck。早期24/browser24雖過，但尚未結束時clean rebuild移除JAR導致artifact驗證FileNotFound，完整run exit1，不採為最終證據；等待所有build結束後固定產物、新scope完整重驗並確認exit0/hash。失敗證據保留、不靠retry掩蓋。
- 圖片上傳／物件治理、身份限流／帳號治理、正式HTTPS／backup等尚未完成；此輪為核心管理里程碑，不宣稱正式商用部署驗收。

## 2026-10-12：Phase4A 管理登入與權限（歷史）

基準5011401。詳見[ADMIN_AUTH](ADMIN_AUTH.md)及[安全報告](verification/admin-auth-phase4a-2026-10-12.json)。僅驗平台，來源作品數字仍為歷史條件。

- 最終Maven clean package：**31**項真PG17.11／real HTTP全部通過，failure/error/skip0（公開與升級20、登入授權11）。前端vue-tsc／production build通過。
- 最終JAR／production dist／全新PG：**18**項runtime與**19**組Chromium151全通過，0retry/flaky/skip。Secure Cookie預設、PBKDF2 CLI建帳登入、Session／CSRF旋轉、Cookie重放拒絕、角色／enabled／version變動失效；JAR restart及same-volume PG restart保留登入，真PG停機health/catalog/auth503。
- Reader不能讀身份／Session、auth不能改身份／作品／schema；真Flyway V1/V2→V3在兩個隔離schema驗證原seed更新與已編輯平台保留、四來源作品不變、無帳號seed、重跑0。V1/V2 git bytes與JAR相同；V3 source/JAR相同，無舊JSON。
- 四個自有scope全部container/volume/network0；JVM／preview停止，自有四ports無listener且TCP拒絕連線。最終手机管理截圖已人工檢視。env／password／hash／有效Cookie及raw trace不提交。
- 初次新增auth測試1fail：只改expiry_time未改Spring Session實際閒置時間，修正fixture後新31全過。cleanup audit一度在測試仍建帳時檢查，完成後重新確認0；另一bare bind遇TIME_WAIT，以實際listener／connection拒絕確認退出，不清他人服務。原證據不覆寫。完整runtime兩輪都18/browser19通過，最終JARhash前後一致。
- 登入限流／帳號治理／絕對Session期限／正式HTTPS仍待後續，未宣稱正式對外身份服務安全驗收；此輪不開始作品CRUD。

## 2026-10-12：Phase3 真PG保存（歷史）

基準86aab84。[PERSISTENCE](PERSISTENCE.md)／[安全報告](verification/persistence-phase3-2026-10-12.json)。本輪只驗平台，不重跑来源作品／模型／App。

- 最終Maven clean package：19個真PG17／real HTTP測試，0failure/error/skip；前端vue-tsc/Vite production build成功。
- 新隔離PG／正式JAR／production dist：10項runtime＋13組Chromium151全部通過、0retry/flaky/skip。真API restart保留row/version；真PG stop→health/list503→same-volume start恢復；七表hash前後不變，沒有JSON fallback。一般browser負面案例仍明確route stub，與真DB故障證據分開。
- 六domain表seed：5作品／3分類／32tech／48關聯／7media／14evidence，2份Flyway history。Reader無UPDATE／schema CREATE，owner僅migration；test-mode在history寫入前拒絕錯DB／demo。
- 首helper命令解析失敗在建立scope前修正。早期全部測試通過後，增加測試隔離guard並重驗，再發現incremental JAR含未使用舊JSON，保留原XML後clean package移除並再次驗證；原結果均保留。Maven clean只清build，未Flyway clean/reseed/覆寫正式資料。
- 六個自有PG scopes之container/volume/network皆0，自有程序停止；25486/25487/19186/15186關閉，其他作品不動。Private env、raw logs/XML/browser trace不提交，Git保存安全hash／摘要；已人工查看最後本輪手機列表截圖，不把瀏覽器視窗算真設備驗收。

下方為Phase2/Phase1歷史結果，保留當時範圍。

## 2026-10-12：Phase2 公開作品 API（最新）

起始main b4cc8af。本輪新增平台後端，未重驗任何來源作品。[API契約](PUBLIC_API.md)／[安全結果與artifact hashes](verification/public-api-phase2-2026-10-12.json)。

- 完整Maven package／Java21／SpringBoot3.5.16：12個真HTTP/random port測試全部通過，failure/error/skip0；無DB／controller stub／跳測試。
- 前端vue-tsc/Vite build通過；相同final package JAR／dist＋Chromium151，13組browser通過、0retry/skip/flaky。常規真API，503／不完整／延遲是明確route stub。320/390px八頁、deep reload、圖片、搜尋與400/404/405契約各由對應測試驗證。
- 兩個自有loopback服務皆停止，API19186／preview15186關閉，沒有其他作品程序／Docker／model／App操作。SDK唯讀借用，cache／proxy設定／raw reports留自己的work。
- 首Maven因JDK自帶CA不信任proxy失敗，改已配置系統Java truststore而非禁用TLS；首browser12pass/1fail為期待文案不符，修正harness再新報告13pass，failure artifacts保留。四個來源作品JSON內容與上輪逐筆相同、catalog與JAR bytes相同。

下方為前輪歷史內容與證據，不代表平台仍無API。

## 2026-10-12：四個主要作品與證據整合（最新）

起始遠端main `4d2eec1`。本輪只重新驗證平台前端，不重跑其他作品／模型／App；來源測試數與品質結果是歷史條件。[安全彙總](verification/project-refresh-2026-10-12.json)／[来源盤點](PROJECT_CATALOG.md)。

- `npm ci --ignore-scripts`成功，原依賴版本保留，只新增固定Playwright1.63.0與兩個依賴；型別檢查、Vite production build成功，Node24.19.0。
- production dist／自己的loopback preview／Chromium151，9組回歸全部通過、0 skip／flaky／retry：首頁四作品、列表五條目、搜尋／分類／空態、四作品reload深連結／限制／固定來源外連、Atlas真實圖片與平台後端待做說明、320/390px八頁不溢出、未知/暫緩slug404恢復。
- 14個固定commit文件均實際存在，2張Atlas圖片與來源bytes相同；已人工查看來源及本輪桌面首頁／手機列表截圖。private源碼可能需要GitHub權限，不宣稱訪客可公開存取。
- 第一輪preview埠15173已有服務，拒絕啟動，改空閒15186，不停止其他服務。第二輪4pass/5fail：h1有既有裝飾句點而測試漏掉，lazy-load圖片尚未捲入視窗；修正測試選取及scroll後第三輪9pass，不改版型或移除圖片檢查。retries=0，三次原報告／failure trace保留ignored work/browser。
- 最後乾淨npm ci後重建，JS/CSS產物名稱與瀏覽器通過版本一致；文檔／metadata收整不更改產品。preview15186已停止，不部署外部HTTPS，沒有平台後端／DB測試。

下方是過去版本紀錄，不能把「Atlas只有BM25／五個占位專案」當成目前狀態。

## 2026-10-06：context 安全規則驗證

- 本輪前端型別檢查與 production build 成功（npm run build）。
- 此專案只有前端，沒有後端測試或建置。

八項規則完整且各倉庫／平台文件一致；條件與日誌雜湊見 [本輪摘要](verification/context-safety-2026-10-06.json)。本輪只改文件，未重跑瀏覽器／外部 HTTPS／真實模型品質。

## 2026-10-06：平台流程同步驗證

- **本輪實測**：前端型別檢查與 production build（`npm run build`）成功。
- 此作品只有前端，沒有後端測試或建置；未為文件變更新增與實作重複的測試。

完整條件與非敏感日誌雜湊見 [本輪摘要](verification/platform-workflow-2026-10-06.json)。此輪沒有重跑瀏覽器、外部 HTTPS、真實模型品質或效能量測；下方原紀錄保持歷史日期與條件。

日期：2026/10/04。此更新僅修改作品集的靜態展示資料、媒體元件與文件，仍是 Phase 1 前端。

## Atlas 成果回填（本次）

- 已核對 `D:\Project\ai-knowledge-assistant` 的 README、架構、VERIFICATION、EVALUATION 與既有 Surefire XML，後端測試合計 20、0 失敗／錯誤／跳過；4 個瀏覽器測試結果依其驗證紀錄。本次沒有重跑 AI 後端、瀏覽器測試或評估。
- 16 題固定離線評估的狀態符合預期為 14/16，來源召回 13/14、原文與版本核對 49/49。Q13 同義詞漏找、Q14 錯誤前提通過門檻仍保留，不宣稱模型準確率或語意正確性。
- 將六張既有真實截圖複製到作品集 `public/projects/ai-knowledge-assistant/`。production 預覽中六張圖片與封面均載入成功，包含替代文字及完整圖片連結。
- 首頁有電商與 Atlas 兩張真實封面；AI 封面保留 blue 主題，電商保留 forest 主題。
- Atlas 詳細頁 320、390、768、1440px 均無水平溢出，暫時的 viewport override 已恢復。
- 列表搜尋 BM25 與 AI 分類皆僅顯示 Atlas，展示 In Progress 與已驗證技術；不再把 Spring AI 列為已使用技術。
- 頁面區分已完成的離線檢索／摘錄、尚未驗證的真實模型、尚未實作的 embedding／向量檢索，並標示 Ollama 測試為 HTTP stub。
- 唯讀檢查 4173 作品集、4175 AI 預覽、18081 API status 皆回應 HTTP 200；沒有操作文件或執行模型。
- 本次作品集 `npm.cmd run build`：型別檢查與 Vite production build 成功。production 預覽瀏覽器 warn/error 紀錄為空。

## 前次電商展示更新的驗證

- `npm.cmd run build`：Vue / TypeScript 型別檢查與 Vite production build 成功。
- 使用 production preview `http://127.0.0.1:4173/` 檢查首頁、作品列表、電商、AI、Developer Portfolio 與 RBAC 詳細頁。
- 電商封面與三張真實截圖皆載入成功；圖片完整連結與替代文字已提供，未完成專案仍使用 placeholder。
- 電商詳細頁於 320、390、768、1440px 視窗下無水平溢出；390px 畫面人工檢查導覽、連結與封面布局。
- 搜尋 H2 僅顯示電商專案；AI 分類僅顯示 AI Knowledge Assistant，手機分類可換行。
- AI 為 In Progress，但已完成範圍仍顯示待驗證。RBAC 為 Planned 且沒有虛構已實作項目。
- Developer Portfolio 架構仍標示前端 mock 資料與後端未實作，電商架構獨立標示 Spring / JPA / H2 路徑。
- 以上 production 預覽的瀏覽器 warn/error 紀錄為空。

## 電商成果資料的依據

來源是 `D:\Project\e-commerce-platform\docs\VERIFICATION.md`、`docs\ARCHITECTURE.md` 與三張實際截圖。本次另核對既有 Surefire XML：`PurchaseFlowTest` 為 17 tests、0 failures、0 errors、0 skipped。這是電商專案上一里程碑的 H2 結果，本次沒有重跑後端測試。

PostgreSQL 尚未執行驗證。登入、訂單擁有者授權、付款、RBAC、Audit Log、部署、多實例／效能壓測均未列為已完成。

本次唯讀連線檢查時，電商 4174 前端與 8080 API 尚未啟動；作品集的本機 Demo 連結因此附上啟動需求，並與尚未提供的公開 Live Demo 分開。

## AI 實作對話（啟動時紀錄）

已建立「AI 知識庫助理實作 — 文件檢索與引用 MVP」，實作目錄為 `D:\Project\ai-knowledge-assistant`。啟動快照確認對話為 active、第一個實作回合正在執行；這不代表 AI 功能已完成，也不代表模型或向量檢索已驗證。
