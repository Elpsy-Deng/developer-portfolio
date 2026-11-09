# 本人密碼更改（Phase4F）

免費、自有PostgreSQL與Spring Security。ADMIN與VIEWER皆可驗證目前密碼後更改自己的密碼；不提供代他人更改、忘記密碼重設、Email、token交付或強制首次改密碼。原角色、enabled與最後ADMIN保護不被更改。

## 操作與API

登入後從管理概覽進「更改我的密碼」。輸入目前密碼、新密碼、再次輸入新密碼；新密碼12–256字元且不可全空白、不可與現有密碼相同。所有提交結果（含失敗）清空三個密碼欄位，切頁也清；不寫browser storage。成功後所有裝置需以新密碼重新登入。

| 路徑 | 行為 |
| --- | --- |
| GET /api/admin/password | 已登入ADMIN/VIEWER；僅本人username/role/enabled/version，不回hash |
| POST /api/admin/password | 已登入＋CSRF；精確JSON expectedVersion/currentPassword/newPassword；204空body |

username取服務端Session身份，不接受客戶端指定帳號。非法型別/unknown fields/長度400 INVALID_PASSWORD_CHANGE；現有密碼錯誤400 PASSWORD_INVALID，重用400 PASSWORD_REUSED；stale version409 PASSWORD_VERSION_CONFLICT，身份交易鎖忙409 PASSWORD_BUSY；stale角色/停用/憑證版本401 UNAUTHENTICATED；PG/配額/交易故障503 AUTH_UNAVAILABLE，不回SQL或任何秘密。身份/密碼回應no-store，匿名401、無CSRF403。

POST遇到timeout/503時不能假定資料未改，因DB commit與HTTP回應不是同一原子操作；先用新/舊密碼重新登入核對、不直接重送。UI提示結果未確認，僅明確204才說成功。429倒數禁用submit、清空秘密、不自動重送；重新核對身份不清同頁倒數，完整頁面reload可清UI計時但server配額仍保存。兩次新密碼不一致只在本地拒絕且清欄位。

## 密碼工作與交易

先驗欄位，auth角色以PG短transaction保存此次配額，再由獨立credentials runtime讀本人hash及非秘密metadata，在Semaphore（每JVM最多2個改密碼工作）中驗目前密碼/編碼新PBKDF2。明文/hash僅服務端內部比對、不得記入回應或稽核；登入本來就有auth hash讀權，帳號管理identity角色仍不能读/改hash。

Hash工作不持身份transaction鎖。進credentials transaction後取與帳號治理相同的try advisory lock，再讀actor當前enabled/role/version與hash，防角色/停用/密碼競爭；CAS WHERE username/version/原hash更新password_hash/version+1，刪除本人全部PG Session、INSERT PASSWORD_CHANGE audit同一交易。任何audit/IO/DB錯誤全rollback，既有hash/version/Session保持。no secret snapshot只存username/role/enabled/version；credentials角色只有INSERT audit，不能SELECT/修改/刪除稽核。

成功回應前invalidate目前request Session及清SecurityContext，防JDBC尾端重存。新請求仍用enabled/role/version核對，舊Cookie與旧密碼無效；不宣稱取消先前已在處理的其他領域請求。兩個並行更改只有一筆成功，另一筆401/409或忙時429，只有一份PASSWORD_CHANGE；新密碼與audit經JAR/PG restart保存。version上限拒絕。

## 持久嘗試配額

使用V6既有admin_login_bucket／auth transaction，password:獨立namespace，原login namespace bytes不改。共用固定300秒window、bucket總量4096與PG advisory admission鎖，SHA256 keys不存原IP/name/secret，過期清理；login与password各有自己的account/實際socket peer/global計數。正常成功或wrong-current皆消耗配額，登入、登出、改密碼成功與API/PG重啟都不清配額。CSRF及明顯欄位錯誤不做KDF/不消耗配額。

| 變數 | 預設／範圍 |
| --- | --- |
| PORTFOLIO_CHANGE_ACCOUNT_LIMIT | 5／1–10000 |
| PORTFOLIO_CHANGE_PEER_LIMIT | 20／1–10000 |
| PORTFOLIO_CHANGE_GLOBAL_LIMIT | 40／1–10000 |

超额429 PASSWORD_RATE_LIMITED、整數Retry-After、no-store；Semaphore/配額鎖忙同樣429/1秒，身份commit鎖忙409。仍只用request.remoteAddr、忽略XFF/Forwarded/X-Real-IP，不啟動framework/native轉送信任。proxy/NAT後用戶共peer配額；fixed-window邊界burst、SHA key字典推測、cold pool短期503與正式perimeter限制保持[LOGIN_SAFETY](LOGIN_SAFETY.md)。

## Role、migration與升級

V8擴identity audit action為PASSWORD_CHANGE（varchar20／原CREATE/UPDATE保持），JSON whitelist不放寬。新portfolio_credentials只SELECT身份/proof需要欄位含hash、UPDATE password_hash/version、SELECT Session principal_name/DELETE Session、INSERT audit；不能改role/enabled、建立/刪帳號、讀Session attrs、讀/改/刪audit、改作品/schema。Server credential可讀hash是驗證必要的受信任邊界，不等於讓使用者查hash。

原V1–V7 immutable。V8只更新符合未編輯Phase4E seed/version的平台介紹，四來源與已編輯平台/既有身份及Session保留；不seed/reset帳號、密碼或配額。新scope自建private credentials role/env；已配置scope重跑enable-credentials不reset。

既有V7 scope**先啟動自己的PG**、首次使用V8 JAR前：

```bash
python scripts/portfolio_db.py enable-credentials --directory work/<原有scope>
```

追加缺少的PORTFOLIO_CREDENTIALS_USER/PASSWORD及LOGIN/CONNECT role，role已在而matching config缺失會拒絕，不能擅自重設。PG已running者直接enable；既有container停机可start，up因舊設定缺新role提醒時PG已先啟動，先enable再繼續up。不purge持久scope或用Flyway clean/baseline/repair。正式環境由operator先配置role/private env與獨立migration；HTTP不用owner/bootstrap。

## 驗收與後續

[VERIFICATION](VERIFICATION.md)與[HANDOFF](HANDOFF.md)區分真PG/HTTP、固定產物restart/故障與UI429/503 stub。Counter epoch與Session Long只調disposable fixture驗時間，非5分鐘/8小時實際soak。原始密碼/有效Cookie/trace/SQL失敗log留private work，Git僅摘要/安全hash。

忘記密碼重設/一次性交付/Email/強制改密碼、事件告警／保留與正式容量、分類/tech治理与分頁、TLS/可信proxy/匿名Session端點防護、coordinated PG+images backup/restore另做。Vite preview非公開部署，Cookie Secure預設保持；只有loopback演練false。沒有付費identity服務或其他作品帳號整合。
