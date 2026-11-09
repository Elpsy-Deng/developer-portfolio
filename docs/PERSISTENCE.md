> Phase5A：新增[同源容器交付](DEPLOYMENT.md)。deploy API不執行migration，只用reader驗schema；owner只進獨立one-shot entry point。原host啟動仍可由可信helper傳入owner執行migration，下方Phase3描述屬歷史，不表示容器API有owner。

> Phase4H：協調offline pg_dump＋local images及全新scope restore已加入；Session data排除、帳號hash／audit／quota／權限與圖片驗證保持，無新migration／schema變更。操作見[BACKUP_RESTORE](BACKUP_RESTORE.md)。

> Phase4G：V9加入分類／技術version、taxonomy_audit及精確writer grants；V1–V8不改、既有V8 scope沿用writer角色與env，沒有新credential或reset。字典／分頁與改名交易見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。

> Phase4F：V8擴安全audit action及獨立credential role；既有V7先enable-credentials，原V1–V7不改。

> Phase4E：V7安全identity audit與獨立identity writer；既有scope首次升級前enable-identity。原V1–V6保持，見[ADMIN_ACCOUNTS](ADMIN_ACCOUNTS.md)。

> Phase4D：V6新增private admin_login_bucket及auth精確DML；reader/writer不能讀，JAR/PG重啟不清配額，Session login-time不因活動續期。原V1–V5不改，見[LOGIN_SAFETY](LOGIN_SAFETY.md)。

> Phase4C：V5新增image_asset與media FK、私有root及引用感知讀取，見[MEDIA_MANAGEMENT](MEDIA_MANAGEMENT.md)。PG+images協調備份仍待做。

> Phase4B：V4新增private project_audit、獨立writer角色與同交易作品管理。既有scope升級先enable-writer；公開reader與auth不擴大寫入權限，見[ADMIN_PROJECTS](ADMIN_PROJECTS.md)。

> Phase4A更新：V3新增admin_account及Spring Session兩表，另有auth runtime datasource／transaction manager；public reader無權讀身份／Session。原V1/V2不變。既有scope首次升級前先enable-auth，HTTP演練明確關閉Secure Cookie；帳號建立、權限與正式HTTPS限制見[ADMIN_AUTH](ADMIN_AUTH.md)。下方Phase3的「無登入」為當時範圍。

# Phase 3 PostgreSQL 資料保存

Java21／Spring Boot3.5.16／Spring JDBC／Flyway，專屬PostgreSQL17資料庫。只有公開GET查詢，未加入管理寫入、登入、RBAC或其他作品的業務資料。

## 模型與版本

- category：名稱與顯示順序；project：slug、分類FK、狀態、published、精選、顯示順序與version。
- technology／project_technology：名稱唯一、關聯與每作品位置唯一。
- project_media：COVER／SCREENSHOT、位置、同源圖片與placeholder；不包含物件bytes，既有public/素材保持原位。
- project_evidence：固定版本文件連結與位置；私人GitHub存取權不由平台授予。
- features／verification／limitations／challenges／architecture等編輯內容保留JSONB陣列，避免為每種文字區塊建立大量表。排序、分類、技術、媒體與證據用關聯約束。

V1建立六張domain表與約束／索引，V2凍結首次seed（5作品、3分類、32技術、48關聯、7媒體、14證據）。只更新平台自己的PG階段內容，其餘作品歷史結果不重算。首次migration在交易中匯入，既有history不重複seed；改資料不需重建JAR，改schema／seed後續需求需新migration或未來管理API。

**已发布V1/V2不可改bytes/checksum。** 不能baseline/clean/repair原DB；缺history的非空schema由Flyway拒絕。source JSON已移除，現在PG是唯一查詢來源，斷線不fallback。

## 資料庫與權限

只接受明確專屬portfolio_demo／portfolio_test；migration strategy在建立history之前確認DB名稱。測試context另外要求portfolio_test，避免fixture寫入demo。名稱檢查不代替操作者確認服務／volume屬於自己；helper核對scope labels及loopback port。

生成器將password保存在work/新目錄（0700／env0600），不打印。PG17只映射127.0.0.1，使用同一專屬volume：

- portfolio_bootstrap：只作此隔離PG初始化與演練，不能提供給public application。
- portfolio_owner：Flyway建表／版本更新，非superuser。
- portfolio_reader：public schema USAGE與SELECT，無表寫入／schema CREATE；應用查詢使用此帳號。

Flyway使用獨立migration連線，runtime Hikari最多4，connect2s／socket3s／pool2s。列表、詳情、分類使用唯讀交易；列表的分類／內容／總量來自同一repeatable-read snapshot。prepared參數與strpos做literal搜尋，不將%/_当wildcard，不接受client SQL。

正式環境的migration執行獨立化、secret管理、TLS／備份與營運權限另验收；此階段不宣稱正式部署完成。

## 使用自己的新scope

先確定25486／25487與API19186／preview15186未被其他作品使用。需Docker Compose v2，本輪只用PG資料容器，沒有app Docker部署。

```bash
# 完整測試只用可拋棄portfolio_test，不能對自己的demo資料跑fixture。
python scripts/portfolio_db.py init --directory work/pg-test --port 25486 --database portfolio_test
python scripts/portfolio_db.py up --directory work/pg-test
python scripts/portfolio_db.py exec --directory work/pg-test -- mvn -f backend/pom.xml clean package
python scripts/portfolio_db.py down --directory work/pg-test --purge

# Demo另建scope，restart用原env／volume，不重跑init。
python scripts/portfolio_db.py init --directory work/pg-demo --port 25487 --database portfolio_demo
python scripts/portfolio_db.py up --directory work/pg-demo
python scripts/portfolio_db.py exec --directory work/pg-demo -- npm run backend:start
```

另一終端npm run dev或build後preview。同源/api仍代理19186。普通停止：自己API/preview Ctrl+C後，`python scripts/portfolio_db.py down --directory work/pg-demo`，**不加purge**；下次up同目錄保留資料。purge只用自有拋棄式驗收scope。helper只操作自己的local Docker socket，保留proxy配置，不global prune或停止其他容器。

不用helper時需自行設定PORTFOLIO_DB_URL／DB_USER／DB_PASSWORD、PORTFOLIO_MIGRATION_USER／MIGRATION_PASSWORD，且提前建立上述權限；未設定就拒絕啟動，不自動連到舊DB。

## 再現保存與故障驗收

```bash
npm run build
python scripts/run_persistence_runtime.py --output work/<全新目錄> --java-home <JDK21目錄>
```

工具另建portfolio_demo隔離scope25487，用正式JAR核對V1/V2與HTTP；只在新scope加temporary probe，驗API restart不覆寫、實際PG stop回503／沒有fallback、同volume start恢復。還原自己的probe後跑production browser13，再核對七表fingerprint未變。finally停自有程序／清自己的拋棄scope，失敗保留private logs/report，不自動重跑。

這個temporary owner SQL只作驗收，不是管理產品功能。資料庫restart保留不等於備份／跨主機HA／長期營運驗收。結果及artifact hashes見HANDOFF／verification。
