# 展示內容版本 2026-10-13

這是經原始碼核對的**PG內容匯入材料**，不是前端catalog fallback。套用步驟、權限、審閱／CAS、partial結果與來源截圖限制見 [SHOWCASE_CONTENT](../../docs/SHOWCASE_CONTENT.md)。

- release.json：五作品patch、full source SHAs與media SHA256。
- architecture/*.dot：可審查的Graphviz節點／邊／caption來源。
- architecture/*.png：本輪Graphviz＋Noto Sans CJK產生並逐張檢視的架構說明圖，非產品截圖；共五張，每張最大1320px。

重產需Graphviz與Noto Sans CJK，逐張 `dot -Tpng <file.dot> -o <file.png>` 後核對圖與更新release中相應SHA；不同Graphviz/font版本可能不同bytes，不在執行匯入時自動重產。正式既有資料先審閱plan，不能把更新source SHA當成覆寫資料授權。
