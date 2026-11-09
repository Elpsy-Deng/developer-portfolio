> Phase5A：新增[私人同源容器交付／獨立migration／相容app回退](DEPLOYMENT.md)，證據見[VERIFICATION](VERIFICATION.md)。V1–V9／seed不改，公開平台介紹仍是Phase4G歷史快照；新階段內容與真實個人資料由管理頁維護，不改舊migration或覆寫已編輯條目。

> Phase5C（2026-10-13）：重新唯讀核對下表固定來源的README／HANDOFF／相關程式，補五作品各三個工程案例與架構說明圖。full SHA／證據查核日在 `content/showcase-2026-10-13/release.json`，平台來源基線a58a6348c3089e2e5429a05d32cfb806c75df739。依[SHOWCASE_CONTENT](SHOWCASE_CONTENT.md)明確ADMIN匯入PG；V1–V9不可變seed仍是歷史快照，既有資料不自动升級。發呆亭／ELPSY／Encore來源產品截圖仍缺；本輪保存的是五張真平台頁合成fixture截圖。歷史品質／壓測數字未重跑，個人資料暫不公開。

> Phase4H：新增操作工具／真隔離backup還原，不改V1–V9或seed／前端作品事實；四來源與原靜態圖保持。平台公開介紹仍是Phase4G的UI/API階段，備份工具見[BACKUP_RESTORE](BACKUP_RESTORE.md)。

> Phase4G：V9只更新未編輯Phase4F/version7平台seed；字典version／audit與精確writer grants新增，V1–V8、四來源作品與既有身份/session保持。

> Phase4F：V8只更新未編輯Phase4E seed/version的自介，原身份/session/source作品保持，原V1–V7不改。

> Phase4E：V7只更新未編輯Phase4D seed/version的平台自介，補帳號治理／最後ADMIN保護；四來源與已編輯資料、V1–V6保持。

> Phase4D：V6新增登入配額，只更新仍符合原Phase4C seed/version/status的平台自介；已編輯條目、四來源作品與V1–V5保持。歷史「限流／Session絕對期限待做」已完成，其他未驗收限制不變。

> Phase4C：V5只更新仍符合Phase4B原seed/version的平台介紹；已編輯條目、四來源作品、V1–V4與原靜態圖保持。上傳素材使用自己private root与PG引用，不自動迁移static素材。

> Phase4B：V4只對仍符合Phase4A原seed/version的平台介紹加入CRUD／稽核與圖片未完成邊界；已編輯介紹與四來源作品保持，V1/V2/V3不改。後續內容可由管理員透過交易及稽核更新，不能把舊seed當最新資料。

> Phase4A：V1/V2原seed與四個來源作品不變；V3只更新仍符合Phase3原seed/version的平台條目，加入管理登入／角色權限及CRUD／正式安全未完成邊界。不是改舊migration或覆寫已有編輯。

# 作品來源與展示範圍

查核：2026-10-12。本輪只重新驗證展示平台；各作品數字是原版本保存的結果，不累加為新的測試總數。先閱讀 main 的 HANDOFF／README，再核對原始碼與證據；對話附件不取代 repository。

## 固定來源

| 展示項目 | 倉庫／來源 commit | 主要核對文件 |
| --- | --- | --- |
| 發呆亭 | fadaiting-camp `407574be535a8409627a65b0ef4cac6bb3f4e0ef` | docs/HANDOFF.md、DEMO_WALKTHROUGH.md、demo-closeout／backup-restore JSON；Booking／CampRolePolicy／Notification／Backup 服務 |
| ELPSY Shop 後端 | shop_bak_ap `339051d` | docs/HANDOFF.md、PreorderOrder／Money／Fulfillment、MobileAuth／Session 服務、V16–V18 |
| ELPSY Shop 網站 | Shop_back `910af78` | docs/HANDOFF.md、preorder checkout／orders 與 Vue routes |
| 顧客 App | Shop_app `cc8d852e24af2b4dd63c51106873d8123ce9d117` | docs/HANDOFF.md、pubspec.yaml、lib/core／features、customer-app-device JSON |
| Atlas | ai-knowledge-assistant `abc49c59da821a2af101da78315448d820850eef` | docs/HANDOFF.md、QUALITY_EVALUATION／VERIFICATION／ROLE_DEMO、RagEngine、QdrantVectorIndex |
| Encore | ticket-platform `18d16a4eed0ca95196302c221ef00526d592f06f` | FEATURE_STATUS／HANDOFF、M6B/C/D 報告、Lua／MQ／庫存服務及629個測試方法 |
| 平台起始 | developer-portfolio `4d2eec1a8d9c37579aa258aa055a776737af7031` | Phase1、Project Plan、型別化靜態資料與元件 |

作品頁的 evidenceLinks 指向上述固定 commit。私人倉庫可解析文件不表示訪客可公開讀取；尚未發布公開 Demo，不以舊 Tunnel 或 loopback 充當公開連結。App 唯讀審查 clone 留在 ignored work/reviews，不包含 APK/SDK 下載或装置測試。

## 成果與限制

- 發呆亭：保存189個後端／21 HTTP／14 browser結果；真金流與通知送達、本機完整復原仍待驗。
- ELPSY：保存260 pass／2 skip、19 HTTP／39 DB核對；App13 pass／1 skip、原APK的4階段／8手動檢查。CI交付APK簽章與hash不同，裝置測試不沿用，UI自動化／黑畫面限制保留。
- Atlas：真RAG與45個後端回歸證據分開；40題37/40、holdout22/24不是獨立準確率，保留誤拒答／漏條件／原因混淆。
- Encore：M6D629 tests與10 browser／7 PG核對；M6B807 HTTP／819 checks、55零違規；M6C74表178列、10組／67零違規。預扣8/12後DIRTY、剩4票未接續，不能宣稱持續高吞吐；最新版完整容器fleet與正式restore未驗。

## 媒體

Atlas desktop/mobile 複製其已提交的 docs/screenshots/milestone4-rag-*.png；已逐張查看，屬歷史合成文件畫面，不重新啟動模型。其餘作品保留裝飾封面與待補媒體，不能拿概念圖當實際產品截圖。舊baseline截圖保留在public/但不再於目前頁面使用。

## 暫緩與後續

日日選物不列入目前展示；RBAC／Python占位不追加為新必做作品。舊slug現在返回404，相關獨立倉庫保持原狀。顧客App包含於ELPSY作品但仍是獨立倉庫，平台共五個條目不代表最終作品數上限。

Phase2改為公開API；Phase3再將相同四作品資料匯入不可變V2 seed及PG關聯，原JSON檔移除。只有平台自己的技術／範圍更新為PG保存，来源作品固定證據不重算。下一個里程碑管理身份／作品CRUD與媒體；PG為唯一來源，V1/V2不可改，各階段保存獨立證據。
