# PostgreSQL＋圖片協調備份與隔離還原（Phase 4H）

這是免費、離線的操作工具，不是公開管理 API。適用本倉庫 `portfolio_db.py` 建立的 PG17 scope、標準 V1–V9 schema、獨立 local image store。Linux／WSL2＋Python3標準函式庫與Docker Compose；`fcntl`及POSIX權限檢查不適用原生Windows。正式部署、排程、遠端備份、加密與PITR另驗收。

## 協調與資料範圍

1. 操作者先停自己這個scope的所有應用、背景工作與owner維護寫入。工具核對明確loopback application port沒有listener；不停止或終止任何別人的服務／DB連線。
2. 自己scope檔案lock防兩次backup/recover同時執行；保存六個標準角色原LOGIN旗標到private journal，再短交易設NOLOGIN。bootstrap管理通道保留；若DB仍有任何其他client，立即拒絕並在finally恢復原旗標。不重設password、角色、enabled、資料或Session。
3. 核對15個標準public資料表／immutable migration checksum、資料指紋與權限、media marker與empty incoming。`pg_dump --format=custom --schema=public`配合相同停止写入窗口的圖片copy；dump前後資料／grants／圖片manifest必須一致才標COMPLETE。
4. 備份包含完整schema／作品及版本、分類／技術／關聯、媒體metadata、作品／字典／身份audit、帳號PBKDF2 hash／role／enabled／version、持久登入與本人改密碼bucket、Flyway history。Spring Session及attributes只保留空schema，不備份它們的data；還原後全部重新登入，避免重播備份時Cookie。
5. 複製所有canonical UUID.png/jpg objects，包含待退役與合法孤兒；保留mtime以維持GC年齡。active／有引用的asset必须存在且bytes/SHA與PG一致；有引用卻retired/purged拒絕。已退役而未引用的檔案可已缺失，保留可重試metadata。不直接刪原圖或incoming；非empty incoming須先人工核對與正常管理回收。
6. 非公開draft與身份audit仍在dump中；DB role明文連線password、bootstrap/env及初始帳號password交付檔不進dump／bundle。新目的scope另外生成DB連線密碼，使用者帳號hash則保留。Git的legacy `public/`圖、JAR/dist、repo與env不包含；還原必須準備匹配checkout與靜態素材，不能把bundle當完整機器映像。

DB bootstrap／filesystem owner是受信任操作者，仍有能力繞過NOLOGIN直接修改資料；必須也停止這些維護工作。這不是對owner的防竄改或線上分散式snapshot保證。dump／manifest hashes用於完整性，不能證明來源；只還原自己可信的備份，不接受第三方上傳的SQL/archive。

## 備份／驗證

scope必须位於本倉庫ignored `work/`、權限700，runtime.env600；圖片必須是該scope的media root。工具只操作確認標籤／loopback mapping的自有PG；沿用proxy／CA／Docker設定，不改其他專案。

```bash
# 先在啟動API的終端停止它（含同scope背景維護），PG保持運行。
python3 scripts/portfolio_backup.py backup \
  --directory work/pg-demo \
  --output work/backups/portfolio-2026-10-13 \
  --application-port 19186

python3 scripts/portfolio_backup.py verify \
  --backup work/backups/portfolio-2026-10-13
```

output必须是新路徑，不能在source scope內；不覆寫舊／失敗backup。bundle目錄700、dump／manifest／seal／圖片600：`database.dump`、`objects/`、`manifest.json`、`COMPLETE`。manifest記錄版本、row指紋／筆數、excluded sessions、grants hash、object bytes／SHA、dump bytes／SHA。COMPLETE最後寫入並fsync；沒有它的失敗output不可還原。所有讀取與複製拒絕symlink ancestry／非正規object檔案、路径跳脫／重複及extra files。

此版驗證上限：每表100000列、每dump512MiB、media合計512MiB、10000 objects、單object10MiB、manifest4MiB；不是正式容量／quota方案。操作逾時或超界拒絕而非部分成功。失敗原始log留自己的private scope，不印secret／SQL到stdout。

普通失敗／SIGTERM／Ctrl-C執行finally還原來源原LOGIN旗標。SIGKILL、PG故障或還原旗標失敗可留下journal；來源API先保持停止，恢復自己的PG再執行：

```bash
python3 scripts/portfolio_backup.py recover-access --directory work/pg-demo
```

只讀與該scope匹配的原旗標，不把原本NOLOGIN的role擅自啟用，不reset任何password；同一scope仍受檔案lock。恢復成功才移除journal。工具拒絕帶未處理journal的新backup，不自動假設已安全恢复。

## 隔離還原

```bash
# work/pg-restored-2026-10-13 必須從未存在；25488必須空閒。
python3 scripts/portfolio_backup.py restore \
  --backup work/backups/portfolio-2026-10-13 \
  --directory work/pg-restored-2026-10-13 \
  --port 25488 --database portfolio_demo
```

- 先驗整個bundle與本checkout immutable migrations，才建立新scope／角色／volume。目的directory存在就拒絕；不支援覆寫source、既有DB或其volume，無`--clean`／Flyway repair/baseline/reset。
- 所有runtime／owner角色先NOLOGIN，bootstrap執行`pg_restore --role=portfolio_owner --no-owner --single-transaction --exit-on-error`。只限新scope的empty public tables。
- PG dump假設public是既有standard schema，會省略其預設PUBLIC USAGE ACL；因此保留新scope标准public，仅從reviewed archive TOC排除一筆`CREATE SCHEMA public`。先去除helper的reader table default grant，防private表在archive ACL套用前继承SELECT；archive後還原DEFAULT ACL。所有table／column／schema／default／DB grants、owner與runtime role限制都必須匹配source指紋，不省略驗證或盲目擴權。
- 比對全部row指紋（Session兩表必須空）、grants、每張圖片與PG asset ledger，才寫RESTORE_READY及恢復備份角色的原LOGIN狀態。資料／權限／圖片檢核失敗時scope保持NOLOGIN，無ready marker，不啟動API，不删除source；最後恢復LOGIN的回應若失敗，須另核對flag與ready狀態再操作；保留log及自己的失敗scope供調查。清理只對這個新生成的失敗scope明確down --purge；不要借其他volume。
- 使用目的runtime.env启动已package的JAR，重新登入核對內容與published/draft存取，再做自己的production preview。不要把source env複製到目的。即使全部指紋相同，首次登入會正常修改新scope的Session／quota，不能因此當成restore drift。

來源可以在backup完成且LOGIN旗標恢復後重新啟動。目的驗收／修改／GC都不能影響來源；隔離restore不等於正式切換。只在之後正式部署的流程規劃DNS／proxy／secrets／停止來源与切回方案。

## 可重現演練與驗收

先完成自己的真PG `Maven clean package`及`npm run build`。下列測試只建立／清理自己的合成scope，不要求下載到使用者電腦：

```bash
python3 -m unittest discover -s scripts -p 'test_portfolio_backup.py'
python3 scripts/run_backup_restore_drill.py \
  --output work/backup-drill/new-run \
  --java-home /path/to/existing/jdk-21
```

要求19186、25487、25488、25489空閒，artifact／dist不可同時重建。7項offline artifact防護與真PG／packaged HTTP驗证內容／audit／identity／quota、public圖bytes／draft拒絕、stale cookie不能用、原password重新登入、VIEWER寫入403、ref409、恢復後寫入版本／稽核、退役／孤兒GC、PG/JAR restart、來源独立／原Session保持；active client／missing／symlink／corrupt／existing target與invalid real archive都必須拒絕。drill為了先核對待回收byte而關閉scheduled media cleanup，但驗收以正常ADMIN cleanup endpoint實測回收；安全檢查與CSRF沒有關閉。成功結果及失敗紀錄見[VERIFICATION](VERIFICATION.md)、[HANDOFF](HANDOFF.md)。

目前是manual offline snapshot、同環境private artifact。缺少加密匯出／異地副本、排程／保留／容量告警、可量化RPO/RTO及PITR；不能把同磁碟檔案當正式災難備援。這些與正式HTTPS／Docker／migration交付另拆里程碑，保持免費優先。
