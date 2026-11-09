> Phase4H：離線backup/restore不重設帳號；restore使用新DB連線密碼，保留帳號hash/role/enabled/version，Session data故意排除，全部重新登入，舊Cookie不能用。來源正常backup後原Session保持。見[BACKUP_RESTORE](BACKUP_RESTORE.md)。

> Phase4G：ADMIN／VIEWER可讀分類、技術及稽核，只有ADMIN能寫；既有登入／Session／CSRF保持，見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。

> Phase4F：本人改密碼、持久配額與全部Session撤銷已加入，見[SELF_PASSWORD_CHANGE](SELF_PASSWORD_CHANGE.md)；忘記密碼重設仍待。

> Phase4E：帳號建立／角色／啟用與最後ADMIN保護、安全audit／Session撤銷已完成，見[ADMIN_ACCOUNTS](ADMIN_ACCOUNTS.md)；下方帳號治理未完成是歷史範圍，密碼重設仍待。

> Phase4D最新：持久登入配額／429 Retry-After、8小時絕對期限與舊Session重登已加入，見[LOGIN_SAFETY](LOGIN_SAFETY.md)。下方是Phase4A原範圍；「限流／絕對期限待做」為歷史狀態。security回應現另含absoluteTimeoutSeconds及實際三項登入上限；auth對V6 counters精確DML，不增加身份寫入。

> Phase4B更新：作品CRUD／version／audit已加入，見[ADMIN_PROJECTS](ADMIN_PROJECTS.md)。下方「未包含作品CRUD」是Phase4A原範圍；身份與Session契約不變。

# 管理登入與權限（Phase 4A）

本里程碑提供身份、Session、CSRF 與管理概覽，不包含作品 CRUD、帳號管理介面、上傳、SSO 或正式部署。免費、自有 PostgreSQL 與 Spring Security，不使用外部付費身份服務。公開作品 API 不需要登入。

## 身份與資料邊界

- `portfolio_reader`：只查公開 catalog，無寫入；V3 撤銷它對身份與 Session 表的 SELECT。
- `portfolio_auth`：SELECT `admin_account`，只可對 Spring Session 兩表做 DML；不能改帳號、作品或 schema。
- `portfolio_owner`：Flyway 專用；`portfolio_bootstrap` 僅自有 scope 初始化／明確 CLI 建帳。HTTP handler 不使用它們。
- V1/V2 已發布 migration 不變；V3 新增身份與 Spring Session JDBC schema，也只更新 version=1 且仍符合 Phase3 seed 標記的平台介紹，保留已編輯資料。其他四作品內容不變。V3 不 seed 任何帳號。
- PBKDF2-SHA256、16-byte random salt、310000 rounds；CLI 產生隨機 48 字元密碼。成功登入後 principal 清除 hash，Session 不保存密碼/hash；沒有 JWT 或瀏覽器 storage token。

## API

所有管理回應 `Cache-Control: no-store`，由服務端角色檢查。回應不帶 hash、Session ID 或資料庫錯誤。

| 方法／路徑 | 條件／行為 |
| --- | --- |
| GET `/api/admin/session` | 匿名可用，回傳 authenticated、username、role、csrf `{headerName,token}`；匿名身份為空字串 |
| POST `/api/admin/login` | URL-encoded username/password，必須帶 session 回傳的 `X-CSRF-TOKEN`；成功204、錯誤401，登入旋轉 Cookie 與 CSRF |
| GET `/api/admin/overview` | ADMIN／VIEWER，回傳 publishedProjects |
| GET `/api/admin/security` | ADMIN，回傳 enabledAccounts 及 idleTimeoutMinutes=30；VIEWER 403 |
| POST `/api/admin/logout` | 必須帶登入後的新 CSRF，成功204，Session 真正刪除；舊 Cookie 重放401 |

未登入讀管理資料401 `UNAUTHENTICATED`；權限不足403 `FORBIDDEN`；缺少／偽造／過期 CSRF 403 `CSRF_INVALID`；錯誤帳密、未知或停用帳號都401 `INVALID_CREDENTIALS`。PG 不可用503 `AUTH_UNAVAILABLE`，涵蓋 filter 外的 JDBC Session 載入／保存，不退回已登入假資料。未實作的管理 API 拒絕403，公開寫入仍405。

Session 由 PG 保存，閒置30分鐘後過期，每分鐘清理過期資料。每次受保護請求重新查帳號 enabled／role／version；刪除、停用、改角色或增加 version 都立即使舊 Session 失效並要求重新登入，客戶端傳入的 role 不生效。未提供密碼／角色更新 HTTP API；未來帳號治理必須同時增加 version。

Cookie 名稱 `PORTFOLIO_SESSION`，HttpOnly、SameSite=Strict、Path=/api/admin，**預設 Secure=true**。只有自有 loopback HTTP 演練明確設 `PORTFOLIO_COOKIE_SECURE=false`；正式環境保留 true，須同源 HTTPS。尚未驗收 gateway／trusted proxy／TLS，Vite preview 不是正式部署。

## 自有 scope 建立帳號

先依 [PERSISTENCE](PERSISTENCE.md) 建立 PG、執行完整 clean package（測試只接受 portfolio_test），再用 packaged JAR 啟動自己的 portfolio_demo，使 migration 完成。新 scope helper 自動建立独立 auth role、private env；不是自動建立管理使用者。

```bash
# demo API 需在另一终端保持运行；自己的 loopback HTTP 才设置 false
PORTFOLIO_COOKIE_SECURE=false python scripts/portfolio_db.py exec --directory work/pg-demo -- npm run backend:start
# migration 完成後，另一終端明確建立帳號：
python scripts/portfolio_db.py create-account --directory work/pg-demo --username myadmin --role ADMIN
python scripts/portfolio_db.py create-account --directory work/pg-demo --username myviewer --role VIEWER
```

密碼只寫入 scope 中 `account-<username>.env`（600／父目錄700），不印到 stdout、不提交；在自己受信任終端讀取後登入 `/admin/login`。CLI 拒絕重複帳號／檔案，不 UPSERT、不重設密碼。請保存 private runtime.env 與帳密，normal down 保留 volume，`--purge` 僅可清自己的 disposable scope。

既有 **Phase3 自有 scope** 在首次啟動含 V3 的 JAR 前先執行：

```bash
python scripts/portfolio_db.py enable-auth --directory work/<原有scope>
```

此操作只新增缺少的 auth role 與 private env，不修改既有密碼或作品；已有 auth role 卻缺少匹配設定會拒絕。不得使用 Flyway baseline／clean／repair，或修改 V1/V2。正式 DB 由部署管理者先配置同等 auth role／CONNECT 及私有 credentials，再跑 migration；此 helper 不接管其他資料庫。

## 驗收與後續

`run_persistence_runtime.py` 會建立全新 disposable PG／私有合成帳號，驗證 HTTPS Cookie 預設、CLI hash 可登入、JAR restart 保留登入、不重設帳號、真 PG 停機503／same-volume 恢復、登出重放拒絕，以及 production browser。一般 browser 的 503 用明確 route stub；真故障另由 runtime 驗證。原始 traces／env 僅留 ignored work，不公開登入畫面中的密碼或有效 Cookie。

下一輪先做作品 CRUD、樂觀版本控制與稽核，另配精確 writer 權限。正式對外前仍需登入限流／暴力嘗試防護、密碼重設／帳號治理、絕對 Session 期限、HTTPS／proxy、獨立 migration 啟動、資料與 Session 保護及備份驗收。本輪不是正式公開登入服務的安全驗收。
