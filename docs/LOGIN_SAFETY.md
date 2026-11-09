> Phase4F：同一bucket表新增password:配額namespace，5/20/40與login分開，共window/總容量；見[SELF_PASSWORD_CHANGE](SELF_PASSWORD_CHANGE.md)。

> Phase4E：帳號與角色治理、最後ADMIN保護、version/revocation、安全identity audit已加入，見[ADMIN_ACCOUNTS](ADMIN_ACCOUNTS.md)。本文件下方帳號治理待做屬Phase4D歷史；限流與期限契約保持。

# 登入限流與 Session 絕對期限（Phase4D）

使用平台自己的 PostgreSQL／Spring Security，無付費服務或 Redis 依賴。帳號建立目前仍使用明確的私有 CLI；帳號管理、密碼重設與正式部署另做。公開 catalog 查詢不受此登入配額影響。

## 持久登入配額

`POST /api/admin/login` 在 CSRF 成功後、查帳號與計算 PBKDF2 前，先提交一筆短 auth transaction。PG advisory transaction lock 讓所有使用相同資料庫的實例共用原子 admission；不持鎖執行密碼計算。鎖忙時回 429／Retry-After=1，不排無限隊列。

預設每個 300 秒固定視窗：帳號 30 次、實際 socket peer 60 次、全站 120 次。視窗從該 bucket 的第一次允許嘗試起算；成功、錯誤密碼、未知／停用帳號都耗用配額，成功登入不清零。任一配額用完回 429 `LOGIN_RATE_LIMITED`、整數 `Retry-After`、no-store；不說明阻擋的是哪個 bucket，不帶帳號/IP。固定視窗邊界可有短時間雙倍 burst，不宣稱 sliding window。

V6 `admin_login_bucket` 保存 SHA-256 bucket key、開始 epoch 秒、次數，沒有原始帳號、IP、密碼或 Cookie。帳號先 strip／lowercase，避免大小寫與空白換 bucket；真正登入帳號的語義不改。hash 可被字典推測，不能宣稱資料匿名化或可公開。表只供 auth SELECT/INSERT/UPDATE/DELETE，reader/writer 無存取，auth 仍不能改帳號或作品。

視窗時間取 PostgreSQL clock；每筆 admission 清理已過期 bucket，表預設最多 4096 筆；新增會超限就通用 429，不無限累積未知帳號。阻擋時不增加次數／不延長視窗。API/JAR 或同 volume PG 重啟不清除配額，過期才可再登入；PG 不可用／限流交易失敗時回 503 `AUTH_UNAVAILABLE`，不退成無限嘗試。每個 JVM 同時最多兩筆密碼工作，忙時同樣 429，先消耗 admission；無自動重送。過長／空白帳號、空或超過 256 字元密碼回通用 401，Tomcat form body 上限 8KiB、參數最多 16；不得將整個反向代理的 body/connection 防護當成已完成。

## 連線來源與代理

目前只使用 `request.getRemoteAddr()`，`server.forward-headers-strategy=none`，忽略 `Forwarded`、`X-Forwarded-For`、`X-Real-IP`。改成 framework/native 會拒絕啟動，不能藉環境參數偷偷放寬。尚未實作可信 CIDR 或跳數解析。

目前 loopback/Vite preview 或一般 proxy 後面的使用者共用同一 peer 配額；正式部署須在隔離 gateway 驗證可信來源、外部禁止直連 API、TLS／cookie／額外全站與匿名 Session 端點限流，之後才擴充 proxy integration。這輪並未建立公開身份服務。CSRF 失敗不耗配額、取得匿名 CSRF 可建立 Session；這些入口仍需要正式 perimeter 防護。

## 絕對 Session 到期

閒置期限仍是 30 分鐘。每次成功密碼認證（Session/CSRF 旋轉後）寫入 PG Session 的 `portfolio.auth.login-time` Long 毫秒；預設登入滿 28800 秒（8 小時）到期，即使持續操作也不能延長。只有重新通過真實密碼認證才刷新時間，不是讀 `/session` 或活動就續期。設定縮短立即作用於既有 Session。

時間缺失／類型不符／未來時間或已過期即 invalidate Session、清掉 SecurityContext。保護路由回通用 401 `UNAUTHENTICATED`（寫入也先拒絕），舊 Cookie 不能復用；`GET /session` 返回匿名身份與新的 CSRF。升級前已登入、沒有 marker 的舊 Session 必須重新登入；不替舊身份自動補新時間。使用應用主機時鐘，正式多實例須有同步時間。

前端 429 後清空密碼、保留帳號、倒數時禁用提交；倒數結束只允許手動提交，不自動重送。「重新載入驗證」不清掉同頁倒數。頁面重載可清除 UI 計時，但 server quota 保持。到期清掉管理概覽／導向登入；編輯表單既有錯誤处理不自動重送寫入。安全概覽顯示實際閒置／絕對期限與三項配額，不顯示 bucket key。

## 設定與升級

| 環境變數 | 預設 | 可用範圍 |
| --- | --- | --- |
| PORTFOLIO_AUTH_ABSOLUTE_SECONDS | 28800 | 1–86400 |
| PORTFOLIO_LOGIN_WINDOW_SECONDS | 300 | 1–3600 |
| PORTFOLIO_LOGIN_ACCOUNT_LIMIT | 30 | 1–10000 |
| PORTFOLIO_LOGIN_PEER_LIMIT | 60 | 1–10000 |
| PORTFOLIO_LOGIN_GLOBAL_LIMIT | 120 | 1–10000 |
| PORTFOLIO_LOGIN_MAX_BUCKETS | 4096 | 3–100000 |

不存在「關閉限流」設定；正式數值須按真流量／NAT／proxy 驗收，不以盲目增加門檻解決錯誤。部署時不要隨意增加既有 window：之前清除的 bucket 無法回復；參數改動需受控發布。

原有自有 scope 已有 auth/writer/media 設定者直接以新 JAR 讓 Flyway 新增 V6，不改 V1–V5、baseline／clean／repair、密碼或使用者資料。V6只更新仍符合未編輯Phase4C seed/version/status的平台自介，已編輯作品与四個來源作品不覆寫。既有未配置 scope 的 enable-auth/writer/media 流程保持 [PERSISTENCE](PERSISTENCE.md)／[ADMIN_AUTH](ADMIN_AUTH.md)／[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)。

驗收工具在PG恢復後分別以公開health及有界35秒、只讀GET session readiness核對兩個連線池；每次觀察status保存，不重送登入／寫入，不放寬任一實際斷言。PG剛恢復但auth pool尚未建立連線時，API仍可能暫時503。

本輪驗證與安全摘要見 [VERIFICATION](VERIFICATION.md)。實際 PG/HTTP測試包含原子配額、未知帳號容量、過期 window、權限故障拒絕、真 peer 與 forged headers、Session 活動不續期／缺 marker／未來時間／舊 Cookie；packaged runtime另驗 JAR/PG restart。測試以 owner 只調整 disposable fixture 的 bucket/Long 時間，沒有等待完整 5 分鐘／8 小時的 wall-clock soak；browser429/匿名到期為明確 UI route stub，不能混稱真 backend expiry 證據。

## 待補與限制

帳號／角色／密碼重設治理、最後 ADMIN 保護、身份變更敏感稽核、獨立 migration 部署、正式HTTPS／可信proxy／匿名Session與connection限流、quota告警與容量調校、PG+images協調備份／還原另做。攻擊者可耗盡某帳號或 NAT 的短期配額；帳號 401 語義一致不代表未知／停用／錯誤密碼的完整 timing 等價。SHA key及serialized Session只保留於受信任私有 DB；不宣稱完整商用防護或跨地區斷電還原驗收。
