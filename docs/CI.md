# GitHub Actions 驗證（Phase5B–5D）

`Portfolio CI` 使用 GitHub-hosted Ubuntu24.04、Java21／Node24／Python3.12，在 main 程式／content bundle變更、指向 main 的 pull request 與手動 workflow_dispatch 執行。actions 固定為查核過的 commit SHA，job上限35分鐘、同workflow/ref只保留最新執行。文件單獨修改不觸發，以避免重跑相同程式；沒有設定 required checks／branch protection。

## 驗證順序

1. checkout（不保留Git credential），安裝SDK、npm lock依賴及Playwright Chromium。
2. 執行Python備份artifact7、CI證據8、展示版本4、HTTPS材料4項防護測試，共23項。
3. 全新 `portfolio_test` 專屬scope、loopback25486與新credential，跑完整Maven clean package（不skip／選取部分Java tests）；安全彙總Surefire counts。隨後purge自己的測試volume。
4. Vue typecheck／production build，核對migration的Git/source/JAR與public Git/source/dist bytes。
5. 另建空的 `portfolio_demo` scope、loopback25490／15190，標準Dockerfile建固定JAR/dist映像。
6. 跑真Nginx／容器API的完整browser與deployment acceptance：角色／CSRF／CRUD／圖片、API/PG停機與restart、migration replay、壞候選拒絕及同schema相容app回退。
7. 在原27容器／43browser後，透過真ADMIN API規劃／套用五作品文字及PNG，驗stale／VIEWER／人工字段／media／audit／重放與no-op／restart；新增17 API檢查、4 PG-backed showcase browser與320／390px；第五個明確Session429 UI stub。無其他作品服務／模型。
8. 原disposable scope purge後，重新建立空PG，以新合成CA／leaf進行真正憑證／hostname驗證的HTTPS API、Cookie／CSRF／role／CAS／內容匯入／gateway來源／匿名Session quota及API/PG restart。不是TLS browser驗收、沒有公開DNS/部署。
9. finally與workflow always step僅清理此CI輸出下登記的兩個可拋棄scope；驗證containers/volumes/networks皆0。整個測試期間不關auth／CSRF／quota，不重建JAR/dist。

## 秘密與證據

workflow只有 `contents: read`，使用一般pull_request，無pull_request_target、正式服務secret、registry推送或自動部署。只有合成資料／新生成的私有credential；不借其他作品資料庫。work scope 0700／檔案0600，命令stdout/stderr寫私有logs，公開step只顯示stage與結果。

唯一upload-artifact路徑為 `work/ci/evidence/summary.json`，保留7日。由程式**選取數值／固定狀態／檔案SHA**生成，包含完整backend counts、browser counts、container checks數量、分開的showcase API與browser統計、cleanup及固定JAR/dist摘要；不複製任意report欄位。失敗／缺report／零tests／skip／flaky／cleanup不完整不能變PASS。8個證據防護tests注入SECRET到XML/JSON細節，確保匯出的證據不含它，並測不完整與負向結果。

raw work、env、XML、帳號hash、Cookie、secret、圖片／trace、JAR與Docker images都不upload。Hosted runner結束後raw diagnostics不保存；故障stage可由safe summary辨識，再用自己的Cloud環境同一commit、新private output重現。沒有把有敏感內容的log當成可公開artifact。

## 本機／Cloud 同一驗證入口

需非root Linux/WSL2、Docker Compose、JDK21/Maven3.9、Node>=22.12、Python3.12與自己的空閒ports。先安裝依賴與Chromium，再跑同一工具：

```bash
npm ci --cache work/npm-cache
npx playwright install --with-deps chromium
chromium_path="$(node --input-type=module -e 'import { chromium } from "@playwright/test"; process.stdout.write(chromium.executablePath())')"
python scripts/run_ci.py verify --output work/ci-new --chromium "$chromium_path"
```

Cloud已有 `/usr/bin/chromium` 時可直接指定；保留session代理／CA。SDK需要自己的Maven settings可傳 `--maven-settings <設定檔>`；檔案不讀出／印出／打包，cache預設在本repo work/m2。PG base有明確自有可用override時，沿用 `PORTFOLIO_PG_IMAGE`；GitHub workflow不指定override，使用Compose的官方固定digest。

拒絕既有output、work以外／symlink、root執行；失敗保留原output，下次用新的名稱。手動清理只接受這個工具建立的private marker與scope：

```bash
python scripts/run_ci.py cleanup --output work/ci-new
```

purge僅限本工具新建的拋棄scope；host合成media／raw private evidence與自建verification images留當次work/daemon供查核，Hosted runner銷毀時一起消失，不global prune或停其他服務。

## 實際完成狀態

本輪本地完整流程與[GitHub首輪Hosted CI](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38351233282)都已成功；105後端／27容器／43browser、build/cleanup/upload全過，實際artifact ZIP只有588bytes的summary.json。workflow語法與實際執行分別記錄於[VERIFICATION](VERIFICATION.md)與[HANDOFF](HANDOFF.md)。workflow檔案存在／actionlint通過不代表Hosted run成功；權限、runner policy或帳號額度若阻擋，會明確記錄，不能以本地結果替代。

CI不包含公開HTTPS／可信proxy、本機容量／長時間運行或正式資料restore。部署與rollback界線見[DEPLOYMENT](DEPLOYMENT.md)。

Phase5C新增驗證已在本地同一入口全流程通過18／105／27＋43／17＋4；[Hosted新commit實際run](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38356320602)成功並已下載唯一safe summary核對105／27／43／17／4與cleanup0，另見[HANDOFF](HANDOFF.md)與[安全JSON](verification/showcase-phase5c-2026-10-13.json)。不是沿用Phase5B Hosted結果。

Phase5D還包含獨立HTTPS safe counts與ENABLED certificateVerification／cleanup。Cloud full-filesystem cleanup有同scope無log fallback，僅限已驗證CI marker/disposable scope，不是任意volume清除。`--compact`僅Cloud明確VFS override，Hosted不指定，實際local失敗／Hosted結果見[HANDOFF](HANDOFF.md)與[HTTPS](HTTPS.md)。

Phase5D最終[Hosted 38386662847](https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38386662847)成功，精確程式576bcf9：115後端／27容器／43原browser／17展示API＋5browser／30真TLS，certificateVerification ENABLED與cleanup0，實際下載701byte唯一summary.json。failureProgress只含數值、source座標／固定fileCode與allowlist enum，不輸出error內容、paths、titles、Cookie／key。完整失敗／修正與數值見[安全JSON](verification/https-phase5d-2026-10-13.json)。
