# 開發交接 — Developer Portfolio

## 2026-10-13：Phase5A 私人同源容器交付（目前里程碑）

起始remote main `7b155dfa6723886f6b44fce0c714a1283c9251c0`。使用者授權上一輪建議的app Docker、獨立migration、loopback Nginx、啟動／升級／相容app回退；只改developer-portfolio，不含CI／公開HTTPS／可信proxy／忘記密碼或其他作品。開始fetch→遠端HANDOFF／AGENTS／README→status/branch/log/diff含staged→main ff-only；壓縮後再次fetch核對基線與全部修改來源，沒有丟棄來源未明的檔案。

cloud runtime skill已讀，起始environment revision354/current/enforced，unrestricted HTTP、無configured secret/identity；保留session proxy／CA與明確local Docker socket、Docker28.4 VFS。SDK唯讀借用JDK21.0.12.1／Maven3.9.11，自有work/m2與npm-cache／logs，Chromium151。本輪沒有精確usage介面，不捏造額度；完成此独立里程碑先保存，不接下個大型階段。

### 已完成

- pinned Temurin21／Nginx1.28 base Dockerfiles，僅複製已測JAR／production dist；context allowlist排除work/env/cache。nonroot、read-only root、cap_drop ALL/no-new-privileges、tmpfs與有界memory/CPU；API不publish host port。
- private loopback Compose：獨立PG volume／operator port、gateway15190、API19186只在network；bootstrap／migration／runtime secret分别掛不同服務，API只掛runtime.properties＋scope media。非rootLinux UID/GID与private700/600對應，media保存於scope host dir。PG官方entrypoint前只讀private bootstrap secret，避免postgres子UID重新讀不到0600檔。
- deploy API禁止Flyway enabled、owner/bootstrap secrets与owner datasource，reader-only完整schema validate；pending/future/checksum拒絕，不能migrate/clean/repair/baseline。獨立CatalogMigration main只用owner三欄properties，非web無Session／scheduler/runtime secret/media；rollback只validate。
- Nginx真正同源API／production SPA、deep links与missing assets/images404。去掉實體目錄SPA路由的directory fallback、relative redirect避免內部埠漏出；不retry upstream寫入、清forwarding、不記URL/query/cookie/body。gateway健康只表示gateway；API readiness由真PG-backed health判斷，Cloud wget probe明确-Y off绕过loopback代理。
- 私有scoped CLI init/database/build/up/stop/down/create-account/rollback與選用compact；scope/path/symlink/private permissions/UID/loopback驗核、lock、image scope label与固定ID、release/artifactSHA、拒覆寫release/tag。up要求自有app先停，先migration再readiness与Nginxreload，成功才寫active-release；故障不自动retry/rollback。stop/down保留PG/media，purge僅拋棄式驗收volume。
- 新container acceptance与Playwright明确own loopback deployed URL，不起host JVM／preview；保留固定JAR/dist，不关闭auth／CSRF／quota。

重要檔案：infra/*Dockerfile、compose.deploy.yml、nginx.conf、.dockerignore；backend/operations三entry/config、application-deploy.yml／application.yml与CatalogDatabaseGuard；DeploymentIntegrationTest；scripts/portfolio_deploy.py／run_deployment_runtime.py、playwright.config.ts；[DEPLOYMENT](DEPLOYMENT.md)、README／計畫／來源／Cloud-local待補／PERSISTENCE／[VERIFICATION](VERIFICATION.md)、[安全JSON](verification/deployment-phase5a-2026-10-13.json)。

### 本輪實際測試／build

- Maven clean package **105 tests／9 suites／failure-error-skip0**，真PG17.11与real HTTP（原99＋新6）；其中config guard用MockEnvironment測property拒絕，不能稱所有6都是HTTP。Vue typecheck＋production build成功；7 Python backup artifact guards、兩新Python compile成功。
- 最終由本倉庫標準app/gateway Dockerfiles建立多層映像，固定JAR `597a652c673e3e43d3ae6bc6ad752974127efa253e69f74a36086691553ba22f`、dist不變；**27容器檢查＋43 production browser全過／exit0／retry-flaky-skip0**。Cloud PG用既有單層PG17.11 base override，fresh own volume與credential；不借用其他作品DB/service。本輪未執行預設官方PG image，不能把base override結果寫成該digest實測。
- 真unmigrated API schema拒絕／0表、一shot迁移9版後退出；inspect非root／RO／cap_drop／無privileged env/secret/media誤掛與host API port；真深連結／缺asset404、ADMIN/VIEWER/CSRF、草稿發布与409、multipart normalized PNG。
- API重啟保持原Session／圖；停API502/504而static200；停PG503無fallback，same-volume PG恢复Session/domain/image；migration replay完整history及編輯內容不變。same-JAR fully configured坏候选enabledFlyway被实际guard挡下，history無改；回退真正前一imageID（與bad不同）只validate，原Session、圖bytes、history保持。
- V1–V9 git/source/JAR與原12public git/source/dist bytes保持；已看真gateway390px合成封面／表單。test／deployment兩scopes containers/volumes/networks0、25486/25490/15190拒絕連線；host合成media/credential移到private驗收目錄，deployment media空。只留ignored work的raw logs/XML/trace/secret/合成圖／image metadata，不提交。

### 失敗與限制（不可省略）

两次Java access visibility compile失败已修正，第三完整package105过。首次runtime因root-owned JAR600不可讀，當時弱exited/empty-table assertion不證明schema guard，該輪不採成功；補0444後真schema guard可驗。兩輪migration VFS ENOSPC保存；gateway/API/Migration正常後wget被Cloud注入代理403，修probe-Y off。browser初輪28/43：directory redirect用了内部8080、missing PNG fixture；修route/probe/fixture。探索gateway build曾FROM完整local image ID誤当registry reference、繼承nonroot无法chmod root COPY，改verified local tag／COPY--chmod；一次missing-release preflight不算验收。随后43browser与24checks過，但bad candidate因缺PORTFOLIO_DB_URL提前被Flyway條件拒絕，未算guard/rollback通過；補完整非secret Flyway URL后最终標準Dockerfile映像27/43全過。沒有放寬權限、修改migration checksum、repair、mock安全或自动retry。

VFS曾只剩數十到數百MiB；optional compact仅export自己無掛載映像、從不export PG/media/secret，記原新ID/root SHA。後續只移除本輪unused verification images及逐ID/time/description辨識的自有build cache，保留失敗log/records；沒有global prune、停其他service或改daemon/其他repo。最終使用標準多層app/gateway，不是假稱只有單層探索就完成。

目前只支持非root Linux／WSL2、matching UID/GID、local socket与private work scope；同origin loopback HTTP，不是公網部署。Cookie Secure默认true，仅init --loopback-http明確关；gateway同peer quota，不按真訪客IP。readiness是觀察不是自動修復。rollback僅同schema相容app，future-history拒絕旧JAR，不DBdowngrade；backup restore新PG-only scope尚无部署scope自動轉換／路由切換。既有43UI故障stub保持（真PG/API故障另驗）；quota/8h不是soak，本輪沒有重跑Phase4H完整48backuprestore或原74host-runtime，只重新7backupguards与新的27container流程。

### 還沒做與下一步

1. 建議下一個独立里程碑 **CI**：GitHub Actions free runner上隔離PG tests105、Vue type/build、Docker build与安全evidence，控制secret与artifact保存，先不自动公開deploy。
2. 另拆 **公開HTTPS／可信proxy**：免費網域/憑證與真入口方案、Secure Cookie/forwarding信任邊界、匿名Session/connection節流、gateway peer quota、本機／公網故障與路由驗收。
3. 備份加密／離機／排程保留／容量告警、正式PG image與使用者Linux/WSL2及正式資料restore／跨schema切換；PITR／RPO-RTO另列。
4. 忘記密碼的免費可信交付、首次強制更改、batch排序／audit導出、媒體總quota/節流/GC告警、可公開原始碼／穩定Demo與真實個人資料／來源合成截圖仍待。

既有seed/platform公開介紹仍為Phase4G歷史snapshot；新階段資料由管理頁維護，不改不可變migration或覆寫已編輯條目。各作品App/模型/真服務／設備補驗仍各自repo，不在本輪。CI/HTTPS未授權，不自行擴張階段。

下一輪先fetch main→讀遠端本HANDOFF／AGENTS／README→status/branch/log/diff含staged→ff-only。所有來源以remote main為準；舊audit snapshot和work不得当现行checkout。操作見DEPLOYMENT；實際raw位於ignored work/phase5a*，完整package3/reports、runtime1–8與失敗build都保留；新验收必须空schema、新private output、不覆盖失败。

Git保存沿用使用者授權的main流程，push可用单次`git -c credential.helper='!gh auth git-credential' push origin main`（不打印token、不改全局credential）；最後核對fetch/remote一致与clean status。完整前輪及更早交接已原樣保存：[Phase4H歷史交接](verification/handoff-phase4h-2026-10-13.md)。本段不取代安全JSON与实际Git核对，尚未完成項不得按旧对话假定成功。
