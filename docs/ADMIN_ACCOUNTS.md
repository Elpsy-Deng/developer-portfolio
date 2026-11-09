> Phase4F：新增本人改密碼與PASSWORD_CHANGE安全audit、獨立credentials writer；identity角色仍不能讀改hash，見[SELF_PASSWORD_CHANGE](SELF_PASSWORD_CHANGE.md)。下方本人改密碼待做是歷史。

# 帳號與角色治理（Phase4E）

平台自有 PostgreSQL／Spring Security、免費，不串接其他作品身份。ADMIN可新增帳號、檢視目錄、改ADMIN/VIEWER角色與啟用狀態、讀最近50笔稽核。VIEWER可以看作品及作品稽核，但不能讀帳號目錄或身份稽核。公開catalog匿名可用。

## API 與操作

| 路徑 | 行為 |
| --- | --- |
| GET /api/admin/accounts | ADMIN；username/role/enabled/version陣列，按username排序 |
| POST /api/admin/accounts | ADMIN＋CSRF；JSON username/password/role/enabled；201安全Account，不回密碼/hash |
| PUT /api/admin/accounts/{username} | ADMIN＋CSRF；JSON expectedVersion/role/enabled；200安全Account |
| GET /api/admin/accounts/audit | ADMIN；最近50筆CREATE/UPDATE、actor、必要前後metadata、時間 |

username固定：3–64小寫ASCII字母/數字/_/-，首字母須字母；不提供刪除或改名，停用保留歷史與不可重用代號。建立初始密碼12–256字元且不可全空白，建議使用密碼管理器產生並先保存，之後以受信任管道交付；回應不再顯示。僅PBKDF2-SHA256/310000輪hash進DB，不存/回傳明文。API建立上限100帳號（停用也算）；既有超量/CLI資料不自動刪除。只有建立帳號可寫hash；此輪不提供更改密碼／重設token／Email交付。

欄位集合與JSON型別精確核對，不接受unknown password_hash、字串boolean/version、改名或未知role。400 INVALID_ACCOUNT，重複409 ACCOUNT_EXISTS、超量ACCOUNT_LIMIT、找不到404 ACCOUNT_NOT_FOUND；503 AUTH_UNAVAILABLE泛化DB/transaction失敗，不送SQL或原始error。所有回應no-store。匿名401、VIEWER403、無CSRF403；原[LOGIN_SAFETY](LOGIN_SAFETY.md)仍生效。

管理UI有初始密碼input與角色/啟用、列表、單帳號editor、最近安全稽核。更改須明確確認，409保留角色/啟用輸入；「載入最新帳號版本」才替換。失敗必清初始密碼，保留代號；不存browser storage、不自動重送，網路失敗先核對結果。只保存相同狀態時是no-op：版本、Session與稽核均不變；更改自己成功後導回登入。390px與既有320/390px回歸見[VERIFICATION](VERIFICATION.md)。

## 交易、最後ADMIN与權限撤銷

獨立identity datasource/transaction manager，用同一PG的短try advisory transaction lock序列化所有身份更改；鎖忙409 IDENTITY_BUSY，由人核對後再操作，沒有自動重送。進交易重新讀actor當前enabled/role/version；過期actor401、非ADMIN403，不能只信先前Session角色。預期版本不符409 ACCOUNT_VERSION_CONFLICT，無寫入或新audit。

啟用ADMIN改為VIEWER或停用前，檢查仍至少一位enabled ADMIN。最後一位不能停用／降權，409 LAST_ADMIN；停用ADMIN不算可用管理員。兩位ADMIN同時各自降權，只有一筆成功，另一筆衝突，永遠保留一位（治理API交易的範圍，非防DB owner直接修改）。真並行HTTP測試與稽核數證據已保存。

有效改動同交易做role/enabled＋version+1、刪除目標所有PG Session、INSERT安全audit；audit或任何一步失敗，帳號與Session一併rollback。self更改在回應前還會invalidate當前request的Session/清SecurityContext，避免request結尾重新保存舊登入。即使先前在途request再存舊Session，每個新請求仍查version/enabled/role、拒絕舊身份；不宣稱取消已在處理的其他領域請求。版本到上限拒絕，no-op不增加版本或撤銷。

建立Hash工作最多每JVM兩個，不在持有身份交易鎖時計算；真正寫入前重新驗actor。所有建立/修改稽核只含username、role、enabled、version，DB JSON key whitelist再拒secret欄位。Audit append-only限runtime，不是簽章或防owner篡改；永久保留/導出政策尚待做。

## 最小 DB 權限與升級

V7新admin_identity_audit；V1–V6不可改。portfolio_identity只可讀admin_account非秘密欄位、INSERT建立所需欄位含hash、UPDATE role/enabled/version、Session principal_name SELECT與DELETE（FK cascade清屬性）、identity audit SELECT/INSERT。不能讀或更新hash、刪帳號、讀Session attributes、寫作品/schema、更新/刪稽核。reader/auth/catalog writer不能讀identity audit；原auth仍只有身份SELECT與Session/配額DML。

新scope helper自动建private identity credentials與role，HTTP不用owner/bootstrap。**既有V6 scope首次啟動V7前**先在自己的受信任環境執行：

```bash
python scripts/portfolio_db.py enable-identity --directory work/<原有scope>
```

只新增缺少的identity role/private env，不重设既有帳密/data；role已存在卻缺matching config會拒絕，重跑完整設定為no-op。正式部署管理者須先配置同等private LOGIN/CONNECT role與env，再執行migration；不能用helper接管其他DB。private env需要PORTFOLIO_IDENTITY_USER/PASSWORD，不能提交或印出。

V7只更新仍符合version5/Phase4D marker的平台預設自介，已編輯內容与四來源保留。CLI create-account僅明確初始provisioning／開發fixtures，不經此HTTP版本/audit機制；owner/bootstrap屬受信任部署邊界，不是日常管理登入。正式credential交付、獨立migration、HTTPS、可信proxy與協調backup/restore仍需另驗；Vite preview是loopback驗收。

## 後續

密碼更改/重設、一次性交付/強制改密碼、敏感事件專用audit與限流可另做；不默認已完成。分類/tech治理、分頁、audit導出、正式proxy/部署/PG+images協調還原也待做。詳細測試及失敗記錄見[HANDOFF](HANDOFF.md)和[VERIFICATION](VERIFICATION.md)。
