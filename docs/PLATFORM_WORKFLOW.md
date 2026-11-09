# 專案展示平台共同工作流程

更新：2026-10-06。使用者指定以下流程適用於**整個展示平台及其各作品倉庫**，包含 Web、後端、App 與後續搶票專案。各作品保持獨立倉庫、測試與交接；本文件統一工作方式，不代表功能、架構或資料需要合併。

## 每輪固定流程

1. 先 `git fetch origin main`，取得該倉庫遠端最新版本；保留任何未提交修改，不 reset／強制切換覆蓋。
2. 先讀遠端的 `docs/HANDOFF.md`，可用 `git show origin/main:docs/HANDOFF.md`；了解上一輪完成內容、待辦、已知問題與下一步。
3. 再查看 `git status`、目前 branch 與最近 commit；確認狀態正常後切換 main，`git pull --ff-only origin main`，才開始修改。分歧需保留並查明，不任意丟棄。
4. 完成每一階段後跑相關測試與 production build。Vue 專案執行型別檢查與正式 build；Java 專案執行相應測試與 Maven package／verify；App 依自身工具鏈驗證。只有前端的作品不需要補一個後端。
5. 測試與 build 成功後更新 `docs/HANDOFF.md`，明確區分本輪實測與歷史結果。
6. 交接至少包含：本次完成內容、重要修改檔案、測試／build 結果、尚未完成事項、已知問題、下一步建議。
7. 最後 commit 並 push 該倉庫 main。push 前再 fetch，保留並整合遠端新提交，再重跑必要檢查；不 force push。此流程已由使用者授權，不再預設只留在功能分支。
8. 確認重要修改已提交、本地與遠端一致；未完成工作記錄原因與續作方法。失敗的檢查不可寫成成功或完成。帳密、模型、資料、依賴及 build 產物依專案規範留在忽略路徑。
9. 下一輪重新以該倉庫遠端 main 為準，不依賴舊 workspace 或對話摘要。

工具鏈、權限或分支保護阻擋時，先完成可審查的工作，記錄具體阻礙，不擅自更改保護規則。已有倉庫若使用 master，先盤點 main 是否存在、歷史與部署依賴；不可為套用流程而自行改名、建立假 main 或切到無關版本。

## 工作階段與 context 安全規則

本規則適用於整個展示平台的各倉庫與長任務；交接及保存進度的優先級高於繼續實作。

1. 長任務期間若能取得 context／token／usage／使用額度狀態，必須主動檢查，至少在規劃下一階段及完成里程碑時核對；不得捏造剩餘數字。沒有可用介面時，依已知限制與任務規模保守安排階段及保存點。
2. 不可等到限制耗盡才交接；開始大型階段前，須預留相關測試／build、更新交接、commit、push 與遠端核對所需的 context 及額度。
3. 判斷剩餘 context 或使用額度可能不足以安全完成下一階段並保存時，立即停止新增大型修改，縮小本輪範圍，先收尾目前工作。
4. 此時優先執行相關測試／production build、更新 `docs/HANDOFF.md`、commit 並 push `main`。交接須寫明目前狀態、已完成與未完成項目、實際驗證結果及續作方式；失敗或未執行的檢查不可標為成功。
5. 交接與保存進度的優先級高於繼續實作；完成保存與必要狀態核對前，不開啟下一個大型工作階段。
6. 每完成一個可獨立驗證的里程碑，就執行相應檢查、更新 HANDOFF、commit 並 push `main`，確認保存後再接下一階段；不要等整個大型任務完成才第一次保存。
7. 發生 context compaction、資訊遺失或目前狀態不明時，必須重新從 repository 原始檔、`git status`、`git diff`（含 staged diff）、`git log` 與 `docs/HANDOFF.md` 確認實際狀態，並核對遠端 `main`，才能繼續。不得僅依對話摘要推定測試已通過、修改已完成或 commit 已推送，也不得丟棄尚未確認來源的修改。
8. 每次階段收尾最後確認 `git status` 乾淨，fetch 後核對遠端 `main` 已包含最新 commit、本地與遠端一致。若測試／build／push 或分支保護阻擋完成，保留安全且可審查的成果，記錄具體原因，不 force push、不偽稱已保存或狀態乾淨。

## 各倉庫落地方式

- 每個倉庫的 `AGENTS.md` 保留本流程摘要，單獨 clone 也能遵守；本文件作為平台共同入口。
- 每個倉庫維護自己的 `docs/HANDOFF.md`，不把其他作品的測試結果抄成自己的驗證。
- 跨倉庫工作只修改本次需求涉及的專案，分別核對 main、測試、build、commit 與 push。一般單一作品任務仍只操作該作品。
- 新倉庫第一次建立時就加入 AGENTS 與交接文件；第一次沒有歷史交接時如實記錄，再以後續遠端 main 接手。
- 真機、個人資料、商家帳號、簽名或部署環境驗收暫緩，列入待補文件。先採免費方案，沒有免費替代時告知使用者。

## 目前盤點範圍（2026-10-12 更新）

| 作品／倉庫 | 流程落地 | 說明 |
| --- | --- | --- |
| [developer-portfolio](https://github.com/Elpsy-Deng/developer-portfolio) | 已維護 main／交接 | Phase3公開API／Vue／PG保存；管理後台後續 |
| [ai-knowledge-assistant](https://github.com/Elpsy-Deng/ai-knowledge-assistant) | 本輪納入 | Atlas；既有功能分支驗證後整合 main |
| [e-commerce-platform](https://github.com/Elpsy-Deng/e-commerce-platform) | 規範仍適用；展示暫緩 | 日日選物工程案例，與 ELPSY Shop 分開，不列目前作品入口 |
| [fadaiting-camp](https://github.com/Elpsy-Deng/fadaiting-camp) | 本輪納入 | 發呆亭；原流程擴充為平台共同規範 |
| ELPSY Shop 後端／Web／App | 已各自維護 main／AGENTS／HANDOFF | 正式A版為 shop_bak_ap、Shop_back、Shop_app；舊master候選不作正式来源 |
| [ticket-platform](https://github.com/Elpsy-Deng/ticket-platform) | 已維護 main／AGENTS／HANDOFF | Encore，M6D受控Cloud展示收尾；DIRTY接續與正式部署待補 |

此表描述規範與接手範圍，不宣稱所有作品已部署、所有遠端倉庫已修改或 App 已實作。未列出的 GitHub 倉庫不自動視為展示平台專案。

## 交接格式

```markdown
# 專案最新交接

更新日期：YYYY-MM-DD；本輪來源：origin/main 的起始 commit。

## 本次完成內容

## 重要修改檔案

## 測試／build 結果

## 尚未完成事項

## 已知問題

## 下一步建議
```

測試項目、命令、資料庫類型與執行環境需寫清楚；H2 不等於 PostgreSQL、模型合約 stub 不等於真實推論、build 不等於正式部署。main 的確切提交以各倉庫遠端 git log 為準。
