# Phase5D：HTTPS 交付與可信 gateway

本版本保留原私人 HTTP deployment，另外提供獨立 TLS scope。訪客 → 此 Nginx gateway（TLS1.2/1.3）→ 無 host port 的 API → 專屬PG；gateway 不信任訪客的 Forwarded/X-Forwarded-*，重新產生自己的 peer claim。API只在明確的自有私有 subnet與constant-time gateway key核對成功時使用單一numeric peer，其他情況回到socket來源。Spring forward-headers-strategy仍none，不自動信任scheme/host鏈。

這是可交付與Cloud驗證的設定，不代表已公開網站。使用者的網域、主機／DNS與真實CA憑證尚未確認；目前Cloud測的是自己loopback scope／臨時合成CA，CA與hostname驗證全程開啟，不使用 -k 或ignoreHTTPSErrors。瀏覽器回歸在原production HTTP Nginx，真TLS／Cookie／角色／CAS／CSRF與重啟由另外的HTTPS驗收工具測，不稱為可信公開網域的browser驗收。

## 建立自己的 TLS scope

先依[DEPLOYMENT](DEPLOYMENT.md)在自己的test scope完整package與frontend build。需要Docker Compose **2.24.4以上**（ports !override）、openssl、非rootLinux/WSL2使用者。準備含完整鏈的PEM certificate與匹配private key，SAN明確含canonical hostname、效期至少24h；key必須為自己擁有的0600 regular file，無symlink。不要貼出key或帳密。

```bash
python scripts/portfolio_https.py init --directory work/deploy-tls \
  --hostname portfolio.example.com \
  --certificate work/tls-material/fullchain.pem --key work/tls-material/private.key \
  --pg-port 25490 --gateway-port 15190 --https-port 15191
python scripts/portfolio_deploy.py database --directory work/deploy-tls
python scripts/portfolio_deploy.py build --directory work/deploy-tls --release tls-r1
python scripts/portfolio_deploy.py up --directory work/deploy-tls --release tls-r1
python scripts/portfolio_deploy.py create-account --directory work/deploy-tls --username portfolio_admin
```

預設HTTP與HTTPS都只綁127.0.0.1，PG operator port仍loopback，API仍不暴露host port。要在使用者確認的實際主機公開，init可明確指定 `--bind-address 0.0.0.0`，使用非特權高埠；主機防火牆、網域、80/443映射與external access由該部署另驗，Cloud不自動執行。實際canonical origin含選定HTTPS port（443時不加port）；HTTP308保留path/query、忽略任意Host重導向，未知Host421。`/healthz`的HTTP200是單純gateway probe，不建立Session或提供管理API。

原已建立scope轉TLS，先協調備份並stop自己的API/gateway，使用同樣選項但改 `portfolio_https.py enable`。要求scope ports一致、持有deploy lock，既有TLS設定拒絕覆寫。PG migration/history/media保持；不要在外部proxy或CDN後直接沿用本模式：它只將直接連線到此gateway的socket視為訪客，不信任額外forwarding鏈。

啟用時核對本scope的PG與唯一自有Docker subnet，更新runtime的proxy network/key、強制Secure Cookie，產生private nginx設定。gateway以scope owner的非root UID/GID讀取0600 cert/key/config；不持有PG bootstrap/migration/runtime secrets或media。API不持有TLS private key，兩者根檔案系統唯讀。key／證書／proxy material不進Docker image、Git、CI artifact或公開回應。operator能讀取自己的scope，是受信任的維運者。

## 管理與內容匯入

瀏覽器需先對canonical hostname的憑證建立正常信任與DNS解析。HTTP不處理admin POST或建立Session；HTTPS Cookie為Secure／HttpOnly／SameSite=Strict，CSRF仍必要。保持登入與自己改密碼功能，沒有新增忘記密碼reset。

對**loopback TLS scope**，展示內容CLI也支援憑證驗證；先審閱plan，再提供計畫SHA：

```bash
python scripts/showcase_release.py plan --directory work/deploy-tls \
  --credentials work/deploy-tls/account-portfolio_admin.env \
  --plan work/deploy-tls/content-plan.json
python scripts/showcase_release.py apply --directory work/deploy-tls \
  --credentials work/deploy-tls/account-portfolio_admin.env \
  --plan work/deploy-tls/content-plan.json --plan-sha256 <已審阅SHA>
```

工具固定連線到scope的127.0.0.1 TLS port，以canonical hostname做SNI／Host與正常CA驗證，不把密碼送到任意URL／redirect。自己private CA可明確加 `--cafile work/<scope>/<可信CA.crt>`；公開受信任CA不用此選項。CLI拒絕公開binding scope，該scope由實際管理頁操作。其他content CAS／partial journal規則見[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)。

## 匿名 Session 配額

所有無有效Session的admin請求先取得PG admission，再讓CSRF建立Session；假Cookie、改username／forwardedheader不避開限流。public API仍stateless。預設每300秒peer120／全站600次，與登入／改密碼使用不同hash namespace，共用4096 bucket容量／PG advisory transaction lock與DB時鐘。匿名Session遇短暫同鎖占用時在3秒交易期限內排隊，避免尚有配額卻因瞬時lock busy誤429；登入／改密碼保留原即時過載拒絕，沒有關掉限制。只存SHA256 key、時間與次數，不存原始IP或username；hash不等於匿名化保證，私有維運資料照原規範處理。

配額計的是建立前的admission，失敗／中斷可消耗一次，不refund或自動重送。過量429／Retry-After／no-store，沒有新Cookie／Session；Session429倒數後才允許手動重載；LOGIN429的既有Session可重載CSRF，但不重置登入倒數。已有Session不消耗新Session配額，仍有原登入／改密碼限流与30min idle／8h absolute expiry。PG不可用或admission讀取失敗503，不繞過限制。API與PG重啟不重置counter；expired buckets依DB時鐘清理，容量滿也拒絕新key。

配置keys：`portfolio.auth.session-peer-limit`、`portfolio.auth.session-global-limit`，窗口與max-buckets沿用LoginLimits。部署properties只在自己scope中配置，不為驗收關閉auth／CSRF／quota。

## 驗收、維運與限制

`run_ci.py verify`在完整package/build、原27／43與showcase17／5後，以**同CI-owned且已purge的空PG scope**做独立真TLS驗收：生成新CA/leaf、驗不可信CA与錯hostname拒絕、Host／308／Secure Cookie／角色／CSRF／CAS、reviewed content import、來源覆寫、lower bounded quota与API/PG restart，最後只purge自己的資源。CA/privatekey/raw/plan/media留private ignored work；safe artifact只有數值/hash/固定状态。四個原showcase browser是真PG內容，新增第五個是明確Session429 UI stub。

Cloud VFS可明確 `run_ci.py verify ... --compact`：只將本次剛建的自有app/gateway無掛載root壓平並退休無容器使用的原verification tags；保留JAR/dist bytes與source image摘要，不export PG/media／secret、不改daemon/global prune。GitHub預設不compact，用標準Dockerfiles映像執行；兩種執行條件在證據分開記錄。

尚無ACME自動簽發／續期、TLS reload/renew CLI、80/443公開入口或正式外網驗收。更新憑證先stop gateway，保存旧cert/key與私有scope備份，以同hostname有效匹配的新cert/key替換自己的0600檔，再明確up；中途失敗先核對，不automatic rollback/retry。只有相容schema的app回退；早於此HTTPS/peer保護的映像不算已驗證的安全回退版本。HSTS只max-age86400，未含includeSubDomains/preload。

兩個socket peer辨識有單元證據，真TLS實測一個loopback/NAT來源與gateway容器IP分離／spoof拒絕；不是多外網訪客／長時間容量驗收。正式網域的憑證鏈、續期、外網來源、瀏覽器信任與資源容量須在選定主機補驗。結果與失敗見[HANDOFF](HANDOFF.md)／[VERIFICATION](VERIFICATION.md)。
