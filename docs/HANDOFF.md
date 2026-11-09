# 開發交接 — Developer Portfolio

## 2026-11-08：自動更新端到端驗證（Developer Portfolio）

本次只增加此交接段落與首頁驗證標記；不變更應用邏輯、資料庫、帳號、映像 recipe、CI 或部署契約。

- 目的：驗證 Atlas／Encore CD 恢復與主機讀取憑證換新後，正常 PR → 原完整 CI → merge → 私人 GHCR 發布 → Ubuntu 主機自然排程部署的完整鏈仍可用。
- 首頁驗證標記由 `CD-VERIFY-20261023-B` 改為 `CD-VERIFY-20261108-C`（瀏覽器測試同步），用於從私人站與公開網址直接確認新版已上線。
- 主機私人部署完成後，再確認 Funnel 公開唯讀版自動跟隨新部署版本重建。
- 未完成上述實測前，不以本段文件或映像發布宣稱自動更新 PASS；結果保存於 server-management 私人交接。

---

## 2026-10-27：main CI 20分鐘逾時 — 最小35分鐘上限修復PR

本輪僅授權診斷Portfolio並建立獨立修復PR、確認原CI成功，不合併main、不手動發布或操作Ubuntu／Timer／其他repo。起始Cloud工作區乾淨，在舊PR #5分支94d0c67；fetch並讀origin/main最新交接／AGENTS／README／workflow，確認main為 `eaee22d3b52a784cc3518d7f240549ab6f184ef9`，由此建立 `codex/portfolio-ci-timeout-35m-20261027`，沒有重用舊PR。

- 已證實根因：run39838224771／job120032060465的GitHub Check Run failure annotation明確為 **The job has exceeded the maximum execution time of 20m0s**。不是只依時間推測；job started 2026-10-22T19:13:42Z、completed19:33:47Z，實際1205秒（20分5秒）。同workflow/main時間窗沒有另一run，沒有concurrency supersession證據；未聲稱取得組織人工取消審計。
- 原始step metadata：Dependencies and Chromium599秒、完整隔離驗收592秒；所有step含cleanup／artifact與post均success，日誌CI outcome: PASS。實際下載700-byte安全ZIP並驗GitHub官方SHA256，只有summary.json且source精確eaee22d3、116 backend／27 container／43 browser／17 showcase API／5 browser／30 TLS、cleanup0全PASS。但整體run/job為cancelled，artifact內PASS不能替代workflow SUCCESS。
- Publisher39841197419在相同source為skipped，原workflow要求真正main push CI conclusion success；保留此限制，不重跑舊main、dispatch正式publisher或繞Ubuntu的VERIFY_RELEASE_ARTIFACT_SOURCE_CI。Ubuntu舊A／新版尚被擋是使用者主機證據，本輪未即時稽核主機；Shop SHOP_STOPPED完全不在範圍。
- 唯一有效設定修改：`.github/workflows/ci.yml` 的 `jobs.verify.timeout-minutes: 20 → 35`，仍有限時。trigger、concurrency、permissions、所有step與action pin、原驗收／清理／artifact、安全與publisher條件均逐項確認不變。`docs/CI.md`同步35分鐘，本交接記錄證據；沒有業務／首頁B／DB／Docker／環境或部署契約變更。
- 本輪本地檢查：YAML解析、與main逐字/結構比對確認僅timeout差異、publisher bytes不變、production typecheck/build及git diff --check通過。未為workflow數值修改重跑本地不變的DB/容器測試；新的PR必須觸發原Hosted完整CI，不以原已取消run的測試PASS作修復成功。
- 原始job/annotations/run metadata與安全摘要留ignored private `work/ci-timeout-investigation-20261027/`；實際新PR Head／Hosted結果見該PR的Checks與描述，提交前不能預記SUCCESS。PR不啟用auto-merge，需使用者確認。
- 合併後main SHA會再次改變：必須等最新合併後main的push CI真正SUCCESS，再由既有Private GHCR發布並交Ubuntu自然CD核對最新source／OCI／Digest／health／資料／journal；不能以舊eaee22d3、PR Head或測試合併SHA作唯一正式目標。Workflow本身也是主機核對項目，若既有主機policy因workflow fence變更停止，保留guard並交server-management另行審閱，本輪不擅自更新policy或承諾必定切換。 本次CI Git blob由 `46cbd9a95bb2b9d0cc0cdd984f807f5bbcc224cb` 改為 `347dec36ca7837b8a7ef526206951f972128f5e5`，實際主機policy未讀取／未修改。

## 2026-10-23：PR #5 改用可區分新舊版的 CD-VERIFY-20261023-B

依使用者最新要求，只更新既有PR #5的 `codex/cd-verify-marker-pr-20261023`，不新開PR、不merge main、不發布或部署。起始PR open/unmerged、Head為 `a7c4d0d7b2de805e5362759cfe9b4799a891948d`、main為 `16265b0133bf54b93ec8db301d601c7b9fed1e9a`，工作區乾淨；核對遠端與舊交接後保留原分支。

- HomeView只將標記改為 `CD-VERIFY-20261023-B`，保留原data-testid、sample-caption、位置與樣式。main來源仍含舊A、不含新B；本輪production JS實際含B、不再含A。
- Playwright共用helper改為檢查新B精確文字且只有一個，另確認舊A為0；既有桌面首頁與320／390px手機案例都使用此helper，仍先等四張精選作品、不加test()／sleep／retry，原43案例數保持。
- 本輪Cloud已通過typecheck／production build、23 Python guards、116 backend／11 suites、27 container／43原browser、17 showcaseAPI／5 showcasebrowser，failure/error/skip/flaky均0。這些是本輪修改工作樹的實測，不沿用前一個Head的CI。
- 本地完整入口並未全PASS：TLS完成27/30後，第28項在同volume PostgreSQL重啟後的匿名Session配額檢查收到503（預期429），source為run_https_runtime.py:144；ready()只檢查public health 200。此步只呼叫API、不執行首頁JS，後端／TLS工具均未改，初步指向既有連線恢復時序，尚未作更廣根因驗證。不得把這次FAIL說成30/30或擅改API／驗證標準。原始失敗與安全摘要留ignored `work/cd-marker-b-pr5-20261023/`，cleanup PASS、自有container/volume/network皆0。
- 必須以新Head觸發的原Portfolio CI及原public-reader PR workflow的實際結果確認；舊a7c4d0d的兩項SUCCESS不能代替新Head。PR description可記錄最新Hosted run／安全artifact證據，保留本地失敗與Cloud/Hosted界線，不盲重跑或隱藏失敗。
- 只有HomeView、portfolio.spec.ts及本交接變更；登入／權限／API／DB／正式資料／依賴／Docker／workflow／環境／部署契約與其他repo保持。後續合併及Ubuntu真版本／Digest／health／資料驗收仍待使用者確認，本輪不發布映像或操作主機。

## 2026-10-23：CD marker 的最小 PR，等待使用者確認才合併

本次只授權獨立分支／PR與PR CI，不准直接push main、自動merge、發Image或操作Ubuntu。起始在已核對的 `homelab/server-contract-20261021-v1` 文件分支，讀取版本化部署契約後 fetch 最新 origin/main／HANDOFF／AGENTS／README／CI與publisher／Dockerfile／既有測試，從產品 main `16265b0133bf54b93ec8db301d601c7b9fed1e9a` 建立 `codex/cd-verify-marker-pr-20261023`；沒有把server-contract文件分支合入。

- 指定 `CD-VERIFY-20261019-A` 已存在main首頁；本次保留同一段文字、位置與sample-caption樣式，只在HomeView既有p加入穩定data-testid，不重複顯示或聲稱首次新增。
- `tests/portfolio.spec.ts` 在原桌面首頁與320／390px手機案例檢查單一標記、精確文字、捲動後可見／位於viewport。手機先等四張真API精選卡片，避免載入位移誤判；未新增test()、維持原43案例與retries=0，不用sleep或盲重試。
- 本輪Cloud實測：npm ci、typecheck／production build、既有完整run_ci.py入口 **23 Python guards、116 backend／11 suites、27 container／43原browser、17 showcaseAPI／5 showcasebrowser、30真TLS checks（certificateVerification ENABLED）全部PASS**；failure/error/skip/flaky均0、標準映像與cleanup PASS／自有container-volume-network均0。安全摘要與raw留ignored `work/cd-marker-pr-20261023/`，本地證據是以16265b0為基底的修改工作樹，不冒稱新commit的Hosted結果。
- 除HomeView、portfolio.spec.ts及本交接，沒有登入／權限／DB／API／正式資料／依賴／環境變數／Port／Volume／Secret／Docker／workflow／發布權限或其他repo修改。原備份／DB／TLS測試只在自己的Cloud隔離scope，不接Ubuntu。
- 分支推送後建立指向main的PR，PR CI實際狀態見該PR Checks；不能用本地PASS替代。PR不啟用auto-merge，等使用者確認。既有publisher只接受同repo exact main的成功push CI，PR的pull_request CI不會發布GHCR。
- 將來經確認合併main後，新的main push會重新跑Portfolio CI，成功才由Private GHCR component release發布固定來源／Digest，再由既有Ubuntu Timer驗證與部署；不能以PR head／PR測試合併SHA當正式main發布來源。
- 2026-10-21主機文件快照記載Portfolio已AUTO至16265b0；Cloud未即時檢查Ubuntu。標記在既有版本已存在，日後只看到相同文字不能證明本次更新，需主機核對新main SHA／OCI／Digest／health／資料／root receipt與journal。保留Caddy/Tailscale/Timer及所有共用infra，沒有正式部署宣稱。

## 2026-10-19：CD-VERIFY-20261019-A／登入 CSRF 並行競態修復

保留首輪首頁標記，使用者授權診斷並修復經證實的問題，全部測試成功後直接提交 main，由既有 CI／GHCR／Ubuntu Timer 接手。起始 fetch 後 origin/main 仍為 `2750b75a1b9d552d4b3d3f51727f4862a82418f5`，保留原兩個未提交檔案；沒有丟棄或重新開始首頁工作。

- 根因是既有程式競態，不是 Cloud 啟動失敗或新首頁回歸。原 API 02:21:56 UTC 已啟動，失敗在 02:22:54；PG ready、Gateway 無 502/504，唯一一次 GET 503 對應 PG 三次 `spring_session_attributes_pkey` 衝突。衝突屬性是 HttpSessionCsrfTokenRepository.CSRF_TOKEN。登入清除舊 token 後，管理頁 onMounted/getSession 與 password spec 等 URL 跳轉後的 csrf helper 並行建立新 token，造成 JDBC INSERT 碰撞，AuthAvailabilityFilter 轉為 AUTH_UNAVAILABLE/503。
- 先加真 PG／HTTP 回歸：用資料表 SHARE lock 讓兩個首次 Session 讀取在缺 token 時重疊，未修復程式確定重現同 constraint 與 **503（預期200）**。觀測使用 authJdbc 讀自己角色的 pg_stat_activity，沒有額外 grants；不關安全、不依賴盲目重試。
- 最小修復：`AdminSecurity.java` 明確使用原種類 HttpSessionCsrfTokenRepository，在 login success handler 生成並保存新 token，與 Session 輪替／登入時間一起於回應前提交。新回歸驗兩個並行 Session 皆200、身份仍有效、兩個回傳 token 皆通過 CSRF、登入前舊 token 仍拒絕。沒有改 DB、API 契約、Cookie、quota、Docker、workflow、部署契約、正式資料或其他作品。
- 本輪先跑認證組 **12/12**，再執行原 `run_ci.py verify` 全流程：**23 Python guards、116 backend／11 suites、typecheck／production build、標準Docker映像、27 container／43原browser、17 showcaseAPI／5 showcasebrowser、30真TLS checks（CA/SAN驗證ENABLED）全過**，failure/error/skip/flaky均0，retries維持0。原 password browser 案例完整保留，並無用重試掩蓋失敗。既有部分 browser 故障 stub 與真TLS API驗收界線保持，不把它們說成全站TLS browser。
- 安全證據：[cd-verify-20261019-a.json](verification/cd-verify-20261019-a.json)。本地結果對應以2750b75為基準的修改工作樹，source patch SHA256與正式HomeView chunk SHA256單列，不把base SHA當已修復版本。原raw、DB credentials與失敗／成功log在ignored work；本輪與首輪自己的容器／volume／network均清理為0。
- 本次 commit 將包含首頁標記、修復、回歸與交接。提交後沿用 Portfolio CI → Private GHCR component release，自動觸發而不修改 workflow；精確 Hosted 結果、來源 revision、api/gateway digest 以該 main commit 的 Actions 及經GitHub artifact ZIP digest核對的 homelab-release/release.json 為準，不能用舊2750b75或本地PASS替代。Cloud 私人 package API 403的限制仍須如實區分；既有 publisher 強制核對 OCI source/revision/recipe 後才交付不可變metadata。
- Ubuntu Caddy／Docker／Tailscale／Timer皆未操作。使用者表示2750b75已正式部署；本輪新版本是否由約15分鐘Timer實際部署，仍由使用者在主機／正式網站確認 `CD-VERIFY-20261019-A`，Cloud不宣稱實體部署完成。

## 2026-10-19 首輪歷史：CD-VERIFY-20261019-A — 本地回歸阻擋，當時未提交／未發布

使用者要求只在首頁底部加入可見標記，測試成功後直接更新 main，交由既有 Portfolio CI／Private GHCR component release 與 Ubuntu CD Timer 處理；若測試失敗不得強制提交。起始 fetch、閱讀遠端 HANDOFF／AGENTS／README／兩個 workflow、核對 status／diff（含 staged）與 ff-only 後，main 為 `2750b75a1b9d552d4b3d3f51727f4862a82418f5`，無分支保護、無既有未提交修改。使用者表示此版本已在 Ubuntu 首次部署成功；Cloud 未操作或獨立確認實體主機。

- 工作目錄修改：`src/views/HomeView.vue` 最後一段加入 `CD-VERIFY-20261019-A`，沿用 sample-caption；沒有改 DB／API／登入權限／Docker／workflow／部署設定／依賴。這段不受開發模式條件限制。
- 本輪通過：npm ci、Vue typecheck／production build、23 Python guards；完整 Maven clean package 的 115 tests／11 suites，failure/error/skip 均 0。以 production dist、Chromium、1440／390／320px 檢查標記可見且位於畫面寬度內；該額外展示檢查明確 stub 公開 API 為 503，不能當完整真 API 回歸。
- 完整既有入口 `scripts/run_ci.py verify` 的標準映像建置成功，但在 container-browser-acceptance 失敗：原瀏覽器 **42 pass／1 fail**，失敗為 `tests/password.spec.ts:28`，在第 31 行操作所用 csrf helper（第 17 行）取得 `/api/admin/session` 時收到 **503，預期 200**。失敗發生在密碼操作之前；不能據此判定首頁標記是根因，也不能宣稱沒有功能回歸。未修改後端或測試來排除此失敗，原因仍待診斷；完整 27 容器／showcase／TLS 驗收未完成。
- 第一輪 Cloud 只有 Java 21 runtime，編譯器不支援 release 21；改在 ignored work/tools 下載並驗 SHA256 的完整 Temurin JDK21 後重跑，上述 115 tests 為修正工具鏈後的實測。Maven 3.9.11 下載亦核 SHA512，未改專案依賴。
- 失敗與原始私有資料保存在 ignored `work/cd-verify-20261019-a/`、`work/cd-verify-20261019-a-jdk21/`；後者安全摘要為 `evidence/summary.json`，outcome FAIL、cleanup PASS。兩輪只清理自己的登記 scope；額外 production preview 已停止。未提交原始日誌、憑證或建置產物。
- 依使用者失敗停止條件，**本次修改與本交接保持未提交**；不 push、不建立 PR、不手動觸發工作流、不變更 Ubuntu。沒有本次 main commit、Hosted CI 成功或新 GHCR digest。既有工作流須待本地阻擋解決且測試成功後由 main push 自動觸發，不能把舊版本成功作本次證據。

下一步：先診斷本輪隔離環境 `/api/admin/session` 503／既有 password browser 回歸；保持本次最小首頁修改，不擅自擴大為登入／API 改造。通過必要檢查後再依授權提交 main，核對精確 CI／publisher run、GitHub artifact ZIP digest、release.json 來源與 api/gateway image digests。私人 package API 在 Cloud 回 HTTP403，來源標籤的核對可依既有 publisher 的 OCI source/revision/recipe 強制驗證與不可變 release artifact，須如實區分直接 registry 查詢。Ubuntu 僅由既有約 15 分鐘 Timer 執行，實體部署仍待主機端確認。

## 2026-10-13：Phase5D HTTPS／可信 gateway／匿名 Session

**本輪已完成交付與Hosted驗收**：程式576bcf90b053f7aa5aaeaf8537fbe931d195a4a5，GitHub [38386662847](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847) SUCCESS；115/11 suites、27/43、17/5、30真TLS，CA/SAN驗證ENABLED与cleanup0，實際701byte ZIP只有summary.json。以下「待驗收」段落是開發歷史，最終結果以下方成功段／安全JSON為準。公開網域與主機仍未提供，未公開部署；個人資料暫不公開。

起始fetch remote main d2211e59ae899765e8910624f4e27c2735798dc6，閱讀遠端HANDOFF／AGENTS／README及status/log/diff含staged，main ff-only且乾淨。使用者同意接續HTTPS；只改本repo，未修改其他作品／啟動其服務或模型。網域與部署位置詢問尚無回覆，個人資料仍暫不公開。前輪交接原樣archive [Phase5C](verification/handoff-phase5c-2026-10-13.md)。

沿用cloud-runtime skill與network/Docker references，revision365/current/enforced/unrestricted，無configured secrets/identities。保留session proxy/CA與local socket，VFS28.4；JDK21.0.12.1/Maven3.9.11唯讀、Node24.19/Chromium151、own work/cache。無精確usage工具，不捏造數字；先保存此獨立階段，公開部署不是Cloud loopback驗收。

### 實作

- PeerAddresses：explicit private narrow IPv4 subnet＋constant-time 256-bit gateway key，只有成功驗證才能採單一numeric peer；任意forwarded/list/hostname/錯key/外部socket回到真socket。套用登入、本人改密碼与匿名Session admission；保持Spring forward strategy none。proxy opt-in要求Secure cookies，錯配置拒啟動。
- AnonymousSessionFilter在absolute expiry之後、CSRF之前，無有效Session才取得PG admission；沿用V6表/精確auth grants／hash namespace，預設300秒peer120/global600、max-buckets4096。既有Session与stateless public保持。真PG並行／配額／DB失敗與fake-cookie測試；登入頁Session429明確倒數＋手動retry。
- portfolio_https.py：新init或停止後enable，驗SAN/keypair/效期/key owner0600、scope/lock/network，private cert/key/proxy config；nonroot owner gateway mount、API無TLS key、owner秘密分離；TLS1.2/1.3、canonical308/未知Host421/SecureCookie/HSTS86400。預設127 only；CLI公開binding只是供確認主機手動選擇，不自動啟動公開網站。TLSshowcase CLI固定loopback TCP＋canonical SNI/Host及CA驗證，private CA可明確cafile。詳見[HTTPS](HTTPS.md)。
- run_https_runtime在CI-own原27/43＋showcase17/5後，原scope purge後新空PG做真CA/SAN TLS/CSRF/role/CAS/content/peer/匿名quota/restart驗證。safe evidence只投影numeric/state/hash，不uploadCA/privatekey/session/raw。23 Python guards（7backup＋8evidence＋4release＋4TLS）。新第五showcase browser是明確Session429 UI stub，原四仍真PG內容；不是TLS browser驗收。
- V1–V9／15表／12 public素材不可變；無新schema/seed或其他作品改動。新增Cloud顯式compact flag僅本次自有無掛載app/gateway，記錄STANDARD/OWNED_COMPACT；Hosted默认標準build/execute，不自動部署。

### 本地實測、失敗與資源

兩輪完整Maven clean package **114 tests／11 suites／failure/error/skip0**，Vue typecheck/build與標準Dockerfilebuild成功；23 Python guards/compile通過。新增5真PG Session checks＋4 peer unit；原105回歸。既有LoginSafety只排除新session namespace統計舊login counters，沒有關掉安全設定。初期evidence unit廣泛fixture replace把43誤改53，修正回43後8全過。

VFS容量：CI1在actual unmigrated schema拒絕後，migration ENOSPC。CI2壓平本次自有images，但runtime mkdir ENOSPC，沒有result不能算PASS。另fresh runtime3再次schema refusal後migration ENOSPC，finally log也寫不下；以確認CI marker/own configuration的同scope direct down/purge救援，counts0。失敗raw與兩FAIL safe summary留private work，不覆寫。只刪本平台前輪／本輪已unused images及逐ID/time/description確認cache，Buildx以child-before-parent回收；不global prune／刪其他project images/data/cache／改daemon或提升權限。回收後約876MiB可用，停止新增本地容器。

補run_ci cleanup：已驗證own disposable scope遇到private log無法建立時，仍以同scope idempotent Compose down/purge清理，無log fallback不放寬scope檢查。實測只fault-inject log ENOSPC，真Docker cleanup／兩scope resource0。原private env/media/raw仍ignored work，不upload。

### Hosted／保存（歷史與最終成功）

初版實作704afa223c4065215a70c88eae8a3b52a149f06f已push，首Hosted run38375753665開始執行。核對既有AuthDatabase後，修正驗收誤寫的Lax為原有SameSite Strict（不改Cookie設定），另push新commit自動取消舊run；只以最新精確commit的完整run當證據。

本地container/TLS尚未完成，不能說全流程成功。114 package/build與guard已驗證，先保存實作與此限制、push main，再依精確commit看GitHub標準映像CI；完成後下載唯一safe summary，核114/27/43/17/5/HTTPSchecks与cleanup0，補正式證據。沒有假稱測過公開域名、真TLS browser、外網多訪客或正式容量。push/Hosted結果尚待收尾，不用前輪run代替。

### 下一步

本輪先完成Hosted真TLS驗證與保存。真公開部署仍需要使用者canonical domain／選定主機／DNS／受信任CA／80/443或選定port／外網來源与browser信任驗收；目前CLI非特權高port、direct gateway ingress，不信任外部CDN/LB鏈。無ACME自動续期/TLS reload CLI、8h soak、跨schema/全服務restore／離機加密備份；app rollback只相容schema且舊於TLS/peer功能映像不是已驗證安全回退。其他作品產品截圖及本機/真機/模型/真provider待補不混本輪。

下輪fetch remote main、讀遠端HANDOFF／AGENTS／README／HTTPS／CI，核status/log/diff含staged與ff-only；不要繼續在滿磁碟產生容器或覆寫原failure。先完成本輪CI／文件commit/remote clean核對，容量需環境恢復後再做下一個大階段。

Hosted fc13ad4/run38376256073實際FAIL，下載416byte唯一safe summary：114/11 suites全過、package/frontend/Docker成功，container-browser stage失敗且cleanup PASS。原raw不upload亦無法從已結束runner讀回，本地VFS也不能重現；因此補安全failureProgress（完成checks、四browser counts、失敗source行號，沒有任意error/title/path），privacy/incomplete unit guards全過。新commit重新完整跑標準Hosted，這個FAIL保留不當成功。

定位LOGIN429舊回歸：原UI允許既有Session在登入倒數時重載CSRF，仍不讓登入提前或自動重送；新增Session429只應阻止沒有Session的重載。修正load/button guard為remaining>0且無session才擋，production typecheck/build通過；Chromium對production dist兩個明確UI stub（LOGIN429 reload保持倒數／SESSION429禁止新Session重載）通過，preview own15192停止。真HTTP/PG/TLS另等最新完整Hosted，沒有把這兩個stub當真API證據。

最新ac52c3f/run38378481329實際FAIL；下載491byte safe summary確認114/11全過、containerChecksCompleted10、原browser42過1失敗（case source line45），cleanup PASS。這次LOGIN429已不是失敗位置；定位帳號雙頁版本case。補失敗assertion的numeric location line（仍不uploaderror文字／檔名／path／Cookie），完整Hosted再跑以取得具體斷點，不用42當43成功。

766be55/run38380032374實際FAIL，但本輪原27/43／showcase17/5全部過（retries/flaky/skip0），114/11 suites過；真TLS已過11個check，停在登入setup/cookie assertion。source未改帳號雙頁case，本輪已過；前兩輪該case有inter-run failure，保留待觀察，不稱修好或無波動。新增TLS numeric operation checkpoint（1/2 provision、3 ADMIN client、4 viewer csrf、5 viewer login、6 Cookie）、HTTP status及Cookie policy bitmask；沒有輸出Cookie值／key／error文字。exact release.Client以合成TLS server實測CA/SNI/CSRF／Secure HttpOnly Strict path cookie replay可用，這是transport fixture，非真Nginx/PG；完整Hosted再定位setup。

144acdd/run38382355635實際FAIL，安全artifact504bytes核114全過、原browser42/43，這次是password case line23、assertion line14（csrf helper直接讀非200回應造成缺token）。定位新Session與登入共用pg_try_advisory lock：即使Session容量仍有，短重疊也會回429。只把anonymous admission改成同一鎖的bounded queue（原3s transaction），保留login/password立即過載拒絕，global capacity仍同mutex原子。新增真PG持鎖／3個guest排隊後全部200 test（115總/11suite待最新Hosted）；兩csrf helper先assert200並只記numericHTTPstatus，沒有body/token。最新改Java必須以新完整package/Hosted驗證，舊114不是115證據。

9fc0724/run38384139862實際FAIL；下載657byte唯一摘要確認新115/11（含held-lock guest queue test）全部過，27/43/17/5完整過，TLS完成11但setup operation3（ADMIN connection）失敗，無API HTTP status exception。增加exact allowlist failureCode與typeCode數值，拒絕任何任意error字串輸出，保留這次FAIL；要先釐清connection precondition／role／TLS transport，不把11當30。

dbda600/run38385333202實際FAIL但115/27/43/17/5全過；667byte safe artifact指出TLS operation3為AttributeError（typeCode7／非HTTP拒絕、無guard failureCode）。本地TLS client/factory fixture通過，Hosted Python版本可能private HTTPSConnection _context變動；改用閉包中明確傳入的驗證SSLContext，不依賴private attr，並加strict numeric source traceback fileCode/line（1 release/2 HTTPS/3 deploy/4 DB/5 TLSruntime），沒有path/locals/error內容。完整Hosted再確認，未宣稱已解決或30通過。

已找到確切TLS harness AttributeError：main內plaintext helper名http，遮蔽global http module，ADMIN connection成功後的http.cookiejar.CookieJar()因此對function取屬性失敗（operation尚為3）。Rename plain_request解除遮蔽；不是憑證／guard／API role錯誤，上一個私有_context改法是保守改善，不當根因。39391c9 run將被最新修正自動取消，不記成功；本地AST/name及compile驗證過，真完整Hosted仍要跑。

### 最終Hosted證據（本輪完成）

程式commit **576bcf90b053f7aa5aaeaf8537fbe931d195a4a5** 已正常push main；真GitHub **[38386662847](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847) SUCCESS**，job115260468459全部SDK/dependency/validation/cleanup/upload/post成功，官方預設pinnedPG、STANDARD app/gateway，沒有Cloud compact override。實際下載artifact11806563370／ZIP **701bytes且只有summary.json**，核對精確SHA、**115 backend／11 suites／fail-error-skip0，27container／43原browser，17showcaseAPI／5browser，30真TLS checks／certificateVerification ENABLED，cleanup PASS/resources0**。新第六anonymous PG test驗short-held lock後三個queued guest全部200，global/peer容量仍不超額；115是本輪Hosted新source結果，不是借本地初版114。Cookie Secure/HttpOnly/SameSite Strict、TLS角色/CAS/CSRF/content/peer與API/PG restart均由真pipeline測。

四個已下載FAIL summaries與兩個cancelled runs保留歷史；不把取消或42/43當成功。部分browser inter-run failures促成bounded Session queue與HTTPstatus診斷；最終完整原43及所有新checks全過／retries-flaky-skip0。固定SQL9/public12 Git/source/packaged保持，JAR/dist在這次acceptance固定，hash見[安全JSON](verification/https-phase5d-2026-10-13.json)。JAR metadata可隨package不同，不宣稱跨build bitwise。Raw/CA/privatekey/credentials/plan/media留私有work；Hosted只upload數值/hash摘要。GitHub connector與檔案服務下載同一artifact，不print signed URL，不改proxy或繞過denied destination。

此後只補docs/安全JSON的文件commit，workflow paths不重跑同source CI；本輪已測程式仍是上面的完整SHA。最後fetch確認local/remote main一致／clean，自己的containers/volumes/networks0、preview15192停止。公開domain/hosting/DNS及信任憑證/外網browser仍須使用者提供與實機驗收，沒有自動公開部署。Cloud VFS容量不足不繼續建立本地容器；下階段先選定部署主機或恢復環境容量，並保持個人資料暫不公開。
