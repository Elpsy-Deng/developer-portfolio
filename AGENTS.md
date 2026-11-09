# Codex Instructions

## Current milestone

Phase 5D: HTTPS delivery, explicitly trusted gateway peer identity and bounded anonymous Session admission, authorized on 2026-10-13. Only modify this repository. Preserve V1–V9, PG-only catalog and existing authentication/CSRF/versions/audits. Verify TLS with a dedicated test CA, never disable certificate verification. Actual public DNS/hosting requires concrete user deployment information; do not claim public launch from private Cloud verification or automatically deploy.

## Phase 5D verification

- TLS keys and gateway authentication material stay in owned private scopes, never images/logs/Git/artifacts. Validate hostname/certificate/key, canonical redirects, Host rejection, Secure cookies and separate runtime secret mounts.
- Accept gateway peer claims only with explicit private network and constant-time secret verification; ignore arbitrary forwarding headers. Verify spoofing, peer separation and bad configuration refusal.
- Bound anonymous admin Session creation before CSRF creates a Session, using durable atomic PG counters; public API remains stateless. Verify concurrency/capacity/restart/failure without weakening auth/CSRF/quotas.
- Keep legacy private HTTP deployment compatible; run full package/build, existing container/browser/showcase regression and separate real verified HTTPS acceptance. Safe CI evidence includes only numeric results and hashes.

## Phase 5C verification

- Curated text/media are versioned import material, never frontend catalog fallback or a silent seed replacement. Use existing ADMIN APIs; preview captures expected versions and hashes, apply rechecks all targets before writes and uses CAS/CSRF. Preserve titles/roles/publish/order/media outside the declared patch; report partial/unknown outcomes without automatic retries.
- Validate source references, diagram labels, media hashes and private plans/credentials. Architecture illustrations are labeled as illustrations; platform captures are not claimed as source application screenshots. Keep source application screenshot gaps explicit.
- Verify real PG-backed content updates, audit/media preservation, replay/stale/VIEWER denial and production desktop/mobile presentation. Run full package/build and existing regression plus separate showcase acceptance; CI evidence must keep those results distinct.

## Technology

- Vue 3
- Vite
- TypeScript
- Tailwind CSS
- Vue Router
- Java 21 / Spring Boot 3.5.16 (public catalog and authenticated admin APIs)
- Spring JDBC / PostgreSQL 17 / Flyway

## Requirements

- Keep components small and maintainable.
- Do not put the entire application in App.vue.
- Use reusable components for project cards, skill badges, navigation, and page sections.
- Keep project facts/evidence in PostgreSQL, with the initial catalog in versioned seed SQL; frontend reads the API, with no static fallback.
- Include responsive desktop and mobile layouts.
- Create Home, Projects, Project Detail, and About routes.
- Project detail pages should include placeholders for screenshots and architecture diagrams.
- Include GitHub and Live Demo links in the data model.
- Run the production build before considering the task complete.
- Keep README instructions up to date.

## Not in scope yet

Do not add:
- External object storage services / password reset or external identity providers
- Automatic deployment / image registry publication

## Phase 5B verification

- Pin workflow actions to verified commit SHAs; use contents:read, unprivileged pull_request, bounded job time/concurrency, no production secrets or repository setting changes.
- CI uses fresh dedicated test/demo scopes and fixed packaged JAR/dist; run full PG package, frontend build, Python evidence guards and actual gateway/container browser acceptance. Always clean only registered own disposable scopes; never global prune.
- Upload only an explicitly generated safe summary of numeric results and hashes, never raw work/, env, XML, session/identity data, screenshots, traces, JARs or container images. Incomplete/failed/skipped checks cannot become PASS. Validate evidence sanitization and failure handling.
- Distinguish local equivalent execution, workflow syntax validation and actual GitHub Actions results. Do not claim hosted success if permissions/billing/policy prevent execution.

## Phase 5A verification

- API deploy profile must reject owner/bootstrap credentials and enabled Flyway before initialization; validate immutable schema with the SELECT-only reader. Migration runs as a separate nonweb entry point with only its own secret mount, no runtime credentials/media/schedulers.
- Build from packaged JAR and production dist; exclude work/env/cache from image context. Verify pinned image IDs/artifact bytes, nonroot read-only API/gateway, no API host port, separate secret mounts and persistent owned PG/media.
- Verify real Nginx deep links/static files, CSRF/session/role CRUD/media, restart preservation, PG/API failure, missing/mismatched schema refusal, migration replay and compatible image rollback without schema downgrade. Logs/fixtures stay private. Run complete PG package and production build before fixed-artifact container/browser acceptance.

- Verify coordinated quiescence, source access restoration on failure, checksums/path/symlink guards, fresh-target-only restore, exact data/audit/identity and media preservation, empty restored sessions, durable quotas and least-privilege grants. Backup artifacts and raw evidence stay private in ignored work/. Preserve V1–V9.
- Verify dynamic taxonomy, stale/concurrent mutation, reference deletion/FK protection, same-transaction taxonomy/project audit rollback, exact writer grants, paginated count/items/ordering/search and stale frontend responses.
- Verify current-password proof, own-user-only changes, wrong/reused/invalid inputs, durable quotas, concurrent/role-change CAS, audit rollback, all-session revocation/restart, secret-free responses/audit and dedicated credential grants.
- Verify same-transaction identity/audit/revocation, concurrent last-ADMIN protection, stale actor/version rejection, minimum DB grants and no password/hash in responses/audit.
- Verify durable atomic login admission, restart persistence, generic errors/Retry-After, bounded buckets and password work, untrusted forwarding headers, absolute expiry/re-authentication and missing legacy timestamps. Never disable security for runtime/browser verification.
- Only modify this repository. Public catalog remains read-only; project CRUD and transactional audit writes are authorized. No connection to other projects' services.
- Verify actual image decoding/dimensions, re-encoding, CSRF/roles, private drafts, rollback/orphan cleanup, retirement/reclaim retries and restart bytes. Preserve published V1–V9 and legacy static images. Never rebuild the same JAR/dist while runtime/browser verification is running.
- Run backend Maven clean package without skipping tests and frontend production build; this is build cleanup, never Flyway clean. Browser tests use the packaged backend and production dist in their own loopback ports: host production preview for the legacy runtime, actual Nginx/container API for Phase5A deployment acceptance.
- Use inherited proxy/CA for dependencies. SDKs may be read from existing installations, but caches/logs/build outputs stay in this repository's ignored work/ and backend/target/.
- Test search/filter contracts, unknown slugs, malformed requests, frontend unavailable/empty/stale-response behavior and deep links; do not call old project test counts this milestone's results.
- Use only this repository's explicitly scoped PostgreSQL database/ports/volumes. Migration owner, SELECT-only public reader and narrowly granted auth and catalog writer runtime roles are separate. Never use bootstrap/owner credentials in HTTP handlers. Verify CSRF, session fixation/logout/restart, immediate role/disable enforcement and secret-free responses. Never baseline/clean/repair an existing database or edit published migration bytes. Verify restart preservation and real database failure without fallback.

## 工作階段與 context 安全規則

本規則適用於整個展示平台的各倉庫與長任務；交接及保存進度的優先級高於繼續實作。

1. 長任務期間若能取得 context／token／usage／使用額度狀態，必須主動檢查，至少在規劃下一階段及完成里程碑時核對；不得捏造剩餘數字。沒有可用介面時，依已知限制與任務規模保守安排階段及保存點。
2. 不可等到限制耗盡才交接；開始大型階段前，須預留相關測試／build、更新交接、commit、push 與遠端核對所需的 context 及額度。
3. 判斷剩餘 context 或使用額度可能不足以安全完成下一階段並保存時，立即停止新增大型修改，縮小本輪範圍，先收尾目前工作。
4. 此時優先執行相關測試／production build、更新 `docs/HANDOFF.md`、在功能分支 commit、push 並開 PR。交接須寫明目前狀態、已完成與未完成項目、實際驗證結果及續作方式；失敗或未執行的檢查不可標為成功。
5. 交接與保存進度的優先級高於繼續實作；完成保存與必要狀態核對前，不開啟下一個大型工作階段。
6. 每完成一個可獨立驗證的里程碑，就執行相應檢查、更新 HANDOFF、在功能分支 commit、push 並開 PR，確認保存後再接下一階段；不要等整個大型任務完成才第一次保存。
7. 發生 context compaction、資訊遺失或目前狀態不明時，必須重新從 repository 原始檔、`git status`、`git diff`（含 staged diff）、`git log` 與 `docs/HANDOFF.md` 確認實際狀態，並核對遠端 `main`，才能繼續。不得僅依對話摘要推定測試已通過、修改已完成或 commit 已推送，也不得丟棄尚未確認來源的修改。
8. 每次階段收尾最後確認 `git status` 乾淨，fetch 後核對遠端 `main` 已包含最新 commit、本地與遠端一致。若測試／build／push 或分支保護阻擋完成，保留安全且可審查的成果，記錄具體原因，不 force push、不偽稱已保存或狀態乾淨。

## 固定工作與交接流程（使用者指定）

本流程適用於整個專案展示平台及所有作品，包含 Web／後端／App 與後續搶票專案。自 2026-10-06 起，遠端 `origin/main` 是每個新工作階段的基準；不得假設本地 workspace 或上一輪功能分支已是最新版本。

1. 開始前先 `git fetch origin main`，取得遠端最新版本；不得以 reset 或強制切換覆蓋未提交修改。
2. 先閱讀遠端最新版 `docs/HANDOFF.md`（可用 `git show origin/main:docs/HANDOFF.md`），了解上一輪完成內容、待辦、已知問題與下一步。
3. 再查看 `git status`、目前 branch 與最近 commit。確認狀態正常後切換 `main` 並以 `git pull --ff-only origin main` 同步，然後才修改。若有分歧或未完成修改，先保留並查明來源，不任意丟棄。
4. 依需求閱讀 `README.md`、`PROJECT_CONTEXT.md`、`README_AI.md`、`TODO_AI.md`、`NEW_MACHINE_SETUP.md` 與相關程式。每個專案各自維護 `docs/HANDOFF.md`；一般單一作品任務仍只操作該作品，跨倉庫修改須在本次需求範圍內。
5. 每一階段完成後執行相關測試與 production build；前端包含型別檢查與 production build，Java 後端包含相應測試與 Maven package／verify；App 依自身工具鏈驗證，僅前端專案不要求後端建置。依實際範圍使用隔離資料庫驗證，不連線到其他專案或正式資料。
6. 測試與 build 成功後更新 `docs/HANDOFF.md`，至少記錄本次完成內容、重要修改檔案、測試／build 結果、尚未完成事項、已知問題與下一步建議；區分本輪實測與歷史驗證。
7. 最後在功能分支 commit、push 並開 PR 到 `main`。`main` 受分支保護（2026-11-09 起）：必須透過 PR、`verify` CI 通過才能 merge，管理員也不例外，禁止 force push 與刪除。CI 通過後以一般 merge 合併；merge 後由自架主機 CD 自動部署，不手動部署。push 前再次 fetch 核對遠端，若前進則整合後重跑必要檢查。
8. 確認重要修改皆已提交、PR 已合併或狀態已記錄，合併後本地 `main` 與遠端一致。無法完成的工作須在交接文件寫明狀態、原因與續作方式；帳密、原始敏感日誌與建置產物不得提交。若因權限或分支保護無法 push，保留可審查的 commit 並如實記錄，不擅自變更保護規則。
9. 下一輪重新從遠端 `main` 開始上述流程，不使用舊交接摘要代替最新版文件。

平台共同規範與倉庫盤點見 [PLATFORM_WORKFLOW.md](https://github.com/jackteng-dev/developer-portfolio/blob/main/docs/PLATFORM_WORKFLOW.md)。每個倉庫都保留本摘要，單獨 clone 仍需遵守；新作品初始化時加入同一規範與交接文件。若既有倉庫只有 master，先盤點歷史與部署依賴，不自行改名或建立假 main。
