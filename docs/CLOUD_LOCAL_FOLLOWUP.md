# 展示平台 Cloud 與本機待補

更新：2026-10-13。各作品獨立維護資料、secret、port、volume与驗收。本文件不把商用功能自動追加為目前展示阻擋。

## Cloud 可接續

- [x] 四個主要作品＋平台的來源、範圍、限制與證據連結回填。
- [x] Spring Boot作品查詢API及Vue整合，保留版型／路由，明確錯誤／空狀態；後續已由PG取代固定JSON。
- [x] PostgreSQL／Flyway保存作品、分類、技術、媒體metadata／關聯及證據；真隔離PG、唯讀runtime、restart與停機恢復。
- [x] 平台管理登入／登出、PG Session／CSRF與ADMIN／VIEWER；真隔離PG與production browser驗收。
- [x] 作品CRUD、draft/publish／排序、version衝突與同交易稽核，獨立writer與真PG rollback／並行HTTP驗證。
- [x] PNG/JPEG圖片上傳／media版本與稽核／private預覽／引用存取／本機adapter安全回收。
- [x] PG持久／有界登入限流與8小時絕對Session期限；JAR/PG restart、實際HTTP與production browser驗收。
- [x] 帳號建立／角色／enabled管理、版本衝突、最後啟用ADMIN保護、同交易安全稽核與Session撤銷。
- [x] 本人驗現有密碼後更改、持久限流、版本／安全audit／全部Session撤銷。
- [x] 動態分類／技術治理與公開／管理分頁；同交易改名／作品版本／稽核與草稿引用保護，見[TAXONOMY_PAGINATION](TAXONOMY_PAGINATION.md)。
- [ ] 忘記密碼的重設／交付、稽核導出／排序操作改善，保持免費與各專案隔離。
- [ ] 補發呆亭、ELPSY、Encore去識別合成截圖；不暴露有效QR、會員資料或credential。
- [x] 五作品工程案例、固定來源、架構說明圖與可審閱ADMIN content release；17 API／4 showcase browser本地及Hosted實測，安全artifact已下載核對。
- [x] 五張真平台頁390px合成fixture保存截圖；不是來源應用產品截圖。
- 個人真實經歷／聯絡資料：使用者選擇暫不公開；不視為此次開發遺漏，不編造。

## 使用者本機可用後

- [ ] HTTPS／網域／可信proxy、Cookie及SPA深連結、最新完整images。
- [ ] 多作品隔離port/env/namespace/volume，核對實際24–32GB環境資源及長時間運行。
- [ ] 各作品持久資料、加密／離機備份與全服務隔離restore／單邊切換。
- [ ] 發呆亭真金流、Email／LINE與手機；ELPSY真provider／Android／Mac iOS／商家簽章。
- [ ] Atlas本機模型與個人文件品質驗收；Encore真手機QR／最新fleet與DIRTY接續後的容量驗收。
- [ ] 對外訪客能讀的原始碼／證據與穩定Demo授權；私人倉庫不假稱公開可存取。

尚未驗收維持未勾選。平台連結不代表遠端服務存活；Cloud頁面回歸不取代任何作品的設備、模型或正式營運驗收。

## Phase4H 補充（2026-10-13）

- [x] Cloud隔離PG＋local images的manual offline協調backup／新scope restore，版本／audit／身份hash／quota／ACL與圖片核對、舊Cookie拒絕及來源隔離；見[BACKUP_RESTORE](BACKUP_RESTORE.md)。
- [x] Phase5A私人app Docker／獨立migration、loopback同源Nginx與相容image回退；見[DEPLOYMENT](DEPLOYMENT.md)。
- [x] Phase5B CI workflow／隔離驗證／安全證據匯出，實際GitHub Hosted首輪已通過105/27/43與build/cleanup/upload；見[HANDOFF](HANDOFF.md)。
- [ ] 公開HTTPS／可信proxy、跨schema／災難切換與使用者本機預設映像容量驗收。
- [ ] 備份加密／異地副本、排程／保留／容量告警、PITR及RPO/RTO；目前private work同磁碟artifact不等於正式災難備援。
- [ ] 使用者本機Linux／WSL2／Docker與正式資料的restore補驗收；本輪不要求下載或部屬到使用者電腦。

## Phase5D（2026-10-13）

- [x] 私人HTTPS交付／可信gateway來源及持久匿名Session admission已完成；115後端、27/43、17/5、30真TLS Hosted實跑及唯一artifact核對成功，見[HANDOFF](HANDOFF.md)。
- 公開網域／主機／DNS与憑證尚待使用者提供；公開上線不能由loopback合成CA驗收替代。
- Cloud VFS容量不足，本地容器驗收沒有PASS；清理已確認自有scope/caches後轉GitHub獨立runner，不global prune或碰其他作品。
