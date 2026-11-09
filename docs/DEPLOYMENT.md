> Phase5D：另有獨立TLS scope、可信gateway與匿名Session配額，見[HTTPS](HTTPS.md)。本文件保留原loopback HTTP步驟；沒有網域／主機即不宣稱public launch。

> Phase5B補充：GitHub Hosted CI已用下方預設官方pinned PG image通過完整package/build與27容器／43browser；[實際結果](VERIFICATION.md)。下方「本輪未跑預設PG」是Phase5A當時條件，現在已由獨立Hosted驗收補上。

# Phase5A：同源容器交付與相容版本回退

本版交付 Linux／WSL2 Docker Compose 的私人 loopback 展示：PG17、Java21 API、一次性 migration 與 Nginx production dist。入口為 `http://127.0.0.1:15190`；API 沒有 host port。公開 HTTPS／網域／可信轉送、匿名 Session 節流與使用者本機驗收仍待後續階段。

## 權限與持久資料

- API 使用 `deploy` profile：禁止自動 migration、owner／bootstrap credential；以 SELECT-only `portfolio_reader` 驗證完整 Flyway history，pending／future／checksum mismatch 均拒絕啟動，不 baseline／clean／repair。其他四組 runtime role 保持原有精確權限。
- migration 是同一固定 JAR 的獨立非 web entry point，只掛 `migration.properties`；沒有 runtime secret、media、Session 或 scheduler。一般啟動 migrate＋validate；rollback 僅 validate。owner 不進 API。
- PG 掛私有 bootstrap secret與專屬 named volume。只另開明確 loopback operator port，供既有帳號／備份 CLI 核對 scope 使用。
- API 掛私有 `runtime.properties` 與該 scope 的 `media/`。生成設定的 Linux UID/GID 就是 API／migration 非 root 身分，配置 0600、scope/media 0700；需在同 UID/GID 啟動。gateway 使用 101:101。API／gateway／migration 根檔案系統唯讀、drop ALL capabilities、no-new-privileges；各自只有 tmpfs 可暫存。
- `work/<scope>/`、其秘密、credential files、release records 和 media 需保存；普通 stop/down 不刪 PG volume 或 media。`--purge` 僅刪該 scope 的 Docker volume，且仍保留 host media，不能對正式展示資料使用。
- image context allowlist 僅含已 package 的 JAR、production dist 與必要 infra；work、env、cache 不進 image。版本記錄固定 image ID 與 JAR/dist SHA，不覆寫 release 名稱或 tag。

## 第一次啟動

需非 root Linux／WSL2 使用者、Docker Compose v2、Python3、JDK21／Maven3.9、Node>=22.12、npm。確認自己的 25486／25490／15190 未被使用。以下測試會修改 fixture，因此只對新的 `portfolio_test`，不能對 demo 跑 Maven integration tests。

```bash
npm ci
python scripts/portfolio_db.py init --directory work/pg-test --port 25486 --database portfolio_test
python scripts/portfolio_db.py up --directory work/pg-test
python scripts/portfolio_db.py exec --directory work/pg-test -- mvn -f backend/pom.xml clean package
python scripts/portfolio_db.py down --directory work/pg-test --purge
npm run build

python scripts/portfolio_deploy.py init --directory work/deploy-demo --pg-port 25490 --gateway-port 15190 --loopback-http
python scripts/portfolio_deploy.py database --directory work/deploy-demo
python scripts/portfolio_deploy.py build --directory work/deploy-demo --release r1
python scripts/portfolio_deploy.py up --directory work/deploy-demo --release r1
python scripts/portfolio_deploy.py create-account --directory work/deploy-demo --username portfolio_admin
python scripts/portfolio_deploy.py create-account --directory work/deploy-demo --username portfolio_viewer --role VIEWER
```

`database` 只建立 PG 與 runtime roles；`up` 先要求自己的 API/gateway 已停止，再執行一次性 migration，成功後才起 API/gateway、等 readiness、reload Nginx 解析新 API IP，最後寫 active-release 記錄。失敗不自動重試／回退，不寫成功記錄。先查看 scope 內對應私有操作 log，再明確處理。

帳號由可信本機 CLI 建立，不是 HTTP reset endpoint。初始 credential 留 `work/deploy-demo/account-<username>.env`，不得貼到公開紀錄；登入後可使用本人改密碼。建立已存在帳號拒絕，不 UPSERT／重設。只在 migration 完成後建立帳號。

### 明確匯入展示內容

新scope的V1–V9 seed為歷史快照；建立ADMIN後，依[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)執行 `showcase_release.py plan`、審閱before/after及圖片、以確認過的SHA執行apply。需要本版本checkout的content bundle與自己運行中的loopback gateway，不是容器啟動自動匯入。既有scope也同樣先審閱；版本／人工修改／media變動會拒絕，跨作品部分完成須核對journal／audit再重新規劃。沒有新schema migration。

預設 Cookie Secure=true，`--loopback-http` 是此次明確的私人 HTTP 展示選項。不要把這份 loopback Compose 直接曝露公網。正式 HTTPS／可信 proxy 須另做驗收後決定 Cookie、來源與節流；目前 backend 忽略 forwarding，gateway 清除 Forwarded/X-Forwarded-*／X-Real-IP，所以 peer quota 以 gateway 為共同來源，不能當作每訪客 IP 限流。

## 停止、再啟動、升級

```bash
python scripts/portfolio_deploy.py stop --directory work/deploy-demo
python scripts/portfolio_deploy.py up --directory work/deploy-demo --release r1
# 完整停止容器／network，但保存 PG volume 和 host media：
python scripts/portfolio_deploy.py down --directory work/deploy-demo
python scripts/portfolio_deploy.py database --directory work/deploy-demo
python scripts/portfolio_deploy.py up --directory work/deploy-demo --release r1
```

升級先停止自己的 API/gateway、背景維護和 owner 寫入，再依 [BACKUP_RESTORE](BACKUP_RESTORE.md) 做協調 PG＋media backup。固定並保留舊 checkout／image／release record；新 checkout 在獨立 test scope 通過 package/build 後，以全新 release 名稱 build，再 `up --release r2`。部署工具不会替代測試，也不保證新 migration 能與舊 app 相容。既有 V1–V9 不改 bytes；新 schema 只能新增 migration，並同步檢查備份契約。

## 相容 app 回退

```bash
python scripts/portfolio_deploy.py stop --directory work/deploy-demo
python scripts/portfolio_deploy.py rollback --directory work/deploy-demo --release r1
```

rollback 使用上一版本固定 image ID，先由其 JAR validate 目前 schema，**不執行 migrate 或 schema downgrade**。本輪實測同 V9 的壞候選（API 開啟 Flyway）拒絕啟動後，回退原 image 保持 Session、圖片與完整 history。若新版已新增 migration，舊 JAR 的 future-history guard 會拒絕回退；需完成事先規劃的資料恢復／切換，不可手改 history、repair 或假稱有 DB rollback。备份 restore 目前输出新的 PG-only scope；轉成新的部署 scope／路由切換尚無自動化，本輪不宣稱完整災難切換。

## gateway 與故障

Nginx 提供 SPA deep link、static 與同源 `/api/`。不存在的 `/assets/` 或圖片回 404；API upstream 故障回 502/504，PG 故障由 API 回 503，沒有 catalog fallback。`/healthz` 只表示 gateway 活著，API readiness 另外核對真 PG。禁止 proxy_next_upstream，避免寫入請求被自動重送。access log 只留 method／status／upstream status，不記 path、query、Cookie 或 request body。

Cloud 會注入代理；gateway loopback health 的 wget 必須 `-Y off`，其餘依賴存取保持 session proxy／CA。API IP 變動後啟用工具明確 reload gateway；直接手改 Compose／繞過工具不會自動更新 release 記錄或 DNS。

## Cloud VFS 與驗收

Cloud 的 VFS 儲存每層／容器副本消耗較大，本輪保存實際 ENOSPC 失敗，僅清理本輪自建未用 image／精確辨識的 build cache；沒有 global prune。可選 `compact` 將**自己建好的無掛載映像** export/import 成單層新 release，保留固定 JAR/dist bytes、user／entrypoint／CMD 及 public base runtime ENV；不 export PG/media 或 container secret。原／新 image ID、root tar SHA 記錄於新的私有 release file。

```bash
# 必須停止自己的 app；來源 release/images 必須存在：
python scripts/portfolio_deploy.py compact --directory work/deploy-demo --release r1 --to-release r1-flat
python scripts/portfolio_deploy.py up --directory work/deploy-demo --release r1-flat
```

完整 container 驗收僅限全新、空表、明確可拋棄 scope：先 init/database/build（必要時 compact），再執行下列工具。工具會建立合成帳號、內容與故障 image，finally **down --purge** 自己的容器/volume/network；不給正式 demo 使用。失敗資料與 trace 留 private work，下一輪用新 output，不能覆寫。

```bash
python scripts/run_deployment_runtime.py --directory work/deploy-acceptance --release r1 \
  --output work/deploy-proof-new --chromium /usr/bin/chromium
```

本版本完整容器驗收在原27容器／43browser後，也明確匯入展示版本、執行17 showcase API／4 browser與restart保存；只對全新可拋棄fixture scope執行。

實際結果／失敗與尚未驗證限制見 [VERIFICATION](VERIFICATION.md) 和 [HANDOFF](HANDOFF.md)。最初以單層 app/gateway 探索空間限制；清理本輪精確辨識的自有快取後，最終已由本倉庫標準 app/gateway Dockerfiles 建立多層映像，27容器檢查／43瀏覽器全過。Cloud PG使用既有單層PG17.11 base override，資料／credential／volume全新隔離；本輪未執行預設官方PG image。使用者本機、公開 HTTPS 或正式容量仍未驗收。
