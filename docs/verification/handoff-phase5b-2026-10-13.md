# 開發交接 — Developer Portfolio

## 2026-10-13：Phase5B CI（最新）

基線remote main 2d0020c66b3e5134a59b6897cd509f27b22b1284；fetch→遠端HANDOFF／AGENTS→status/branch/log/diff含staged→main ff-only，起始乾淨。使用者同意先做CI；只改本repo，不自動部署／公開HTTPS／可信proxy／忘記密碼或其他作品。

沿用cloud runtime skill、network/Docker參考；revision358/current/enforced/unrestricted，無configured secret/identity。保留proxy/CA與明確local socket、VFS28.4；SDK唯讀JDK21.0.12.1/Maven3.9.11，自己的work/cache/log、Node24.19/Chromium151。無精確usage工具，不捏造額度，完成此獨立階段先保存。

### 完成內容

- .github/workflows/ci.yml：官方五actions v4 SHA pin（本輪查核tag）、Ubuntu24.04／Java21／Node24／Python3.12；push main程式paths、pull_request main與manual dispatch，contents:read／checkout不存credential、20min timeout／同ref取消舊run。只安裝依賴與驗證，無registry publication/deploy/production secrets。文件單獨改不觸發，未改branch protection。
- scripts/run_ci.py：fresh private marker/work scope、非root、完整PG package／Python guards／frontend build、SQL/public Git-source-package checks、固定JAR/dist標準Dockerbuild和原27/43真gateway驗收。完整finally＋alwaysstep／idempotent cleanup僅自己的test/demo DB；不global prune。
- ci_evidence.py／test_ci_evidence.py：只project數值與固定狀態/hash；XML/JSON raw SECRET不輸出、缺/零/負/failed/skipped/flaky/cleanup不完整拒絕PASS。命令raw log／env／trace／Cookie/hash／media留private，不upload；唯一artifact為safe summary、7日retention。Hosted runner結束raw丟棄，失敗用本地同入口新output重現。
- [CI](CI.md)、README／計畫／Cloud-local／[VERIFICATION](VERIFICATION.md)與[安全JSON](verification/ci-phase5b-2026-10-13.json)；完整前輪交接原樣archive於[Phase5A](verification/handoff-phase5a-2026-10-13.md)。無Java/Vue業務/migration/seed修改。

### 本輪實測與失敗

新6evidence＋原7backup guard tests通過、Pythoncompile及actionlint1.7.12通過。第一次完整run backend105/type-build/Docker過，但VFS ENOSPC導致unmigrated API在media建立前失敗，嚴格assertion拒絕當schema guard成功；safe summary FAIL且cleanup PASS，raw保留work/phase5b-ci1。

只移除自己先前拋棄驗收scope的unused images和本輪逐ID/時間/description辨識快取，不碰其他project images/data/cache、不改daemon/global prune。第二全新private work/phase5b-ci2 **full clean package105／9 suites／fail-error-skip0，Vue typecheck/build，標準Dockerbuild，27container＋43browser全過／exit0／retry-flaky-skip0**；重新跑全部原功能，不把Phase5A數字当本輪。SQL V1–V9 Git/source/JAR与12public Git/source/dist保持，驗收artifact前後固定；hash在安全JSON。Cloud PG17.11既有單層base override、全部自己的新volume/credential；Hosted預設官方pinnedPG尚待實跑。finally兩scope容器/volume/network0、額外cleanup重跑成功；private host合成media/credentials保留ignored work，不upload。

### Hosted／Git保存狀態（已確認）

CI實作commit **54e2ad20d970e22a7228f7c0ae0eca30b93319e3** 已正常push main、fetch核對一致/clean。GitHub首輪 **[38351233282](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38351233282) SUCCESS**，job115143878255所有SDK/dependency/validation/cleanup/upload/post步驟成功（UTC05:24:38–05:31:20，同日Asia/Taipei）。Hosted使用workflow預設官方pinned PG image，沒有Cloud單層override。

實際下載唯一artifact11791365950，ZIP **588bytes且只有summary.json**；核對commit精確54e2ad2、backend **105／9 suites／fail-error-skip0**、container **27**／browser **43／unexpected-flaky-skip0**、cleanup PASS/各resource0、migration9/public12與hash。不是以本地結果推論Hosted成功。Hosted distTreeSHA與local一致；JAR每次package metadata/hash可不同，僅驗各run固定bytes與Git/source/JAR SQL，不稱跨build bitwise reproducible。

GitHub Actions admin設定API403但workflow/list/runs/push正常，未改policy/billing/branch protection。CLI下載artifact的Azure redirect遭Cloud403；使用既有GitHub connector取得同一artifact，再讀其檔案服務副本，沒有更改proxy/policy或直接繞過拒絕的destination。再查runtime revision358/current/enforced。signed下載URL不寫進Git或交接。最後補本段／JSON的文件commit不觸發另一輪相同CI（paths filter），程式仍為已驗證54e2ad2；再次fetch/remote/clean核對，不force push。

### 下一步與界線

本輪先完成CI並保存。下一個展示工作：新階段技術決策/證據、四作品合成截圖與真實個人經歷（使用者提供，不編造）。公開HTTPS／可信proxy／peer quota／匿名Session節流另拆；本機/設備/模型/真provider、備份加密離機/排程／正式restore切換仍待。既有UI部分503/429/expiry是明確stubs，真API/PG故障另測；相容V9 app rollback不等於schema downgrade，PG restore仍PG-only新scope。現在未包含完整48backup-restore重跑、8h soak或正式營運。

下一輪fetch main、讀遠端本HANDOFF／AGENTS／README／CI，再核status/branch/log/diff與ff-only。新CI輸出不可覆寫舊failure，不能借audit snapshot/其他repo DB；使用自己的test/media/loopback scopes。保存優先，不混大型HTTPS階段。
