# AI Knowledge Assistant — 里程碑與後續規劃

## 目前狀態

**In Progress / Atlas 離線檢索第一里程碑完成。** 程式目錄為 `D:\Project\ai-knowledge-assistant`，2026/10/04 已核對其 README、架構、VERIFICATION、EVALUATION、Surefire 測試報告及真實截圖。作品集仍使用 Phase 1 靜態展示資料，未進入平台 Phase 2。

目前為 BM25 關鍵字檢索與原文摘錄 baseline，沒有 embedding、向量資料庫或 LLM 自由生成。Ollama 原文選取 adapter 僅經 HTTP stub 合約測試，真實推論尚未驗證。Spring AI 尚未加入；關鍵字檢索不能稱為向量 RAG，原文摘錄不能稱為 LLM 生成。

## 展示定位

以 Java 後端為主要技術方向，建立「回答可核對、檢索可觀察、品質可比較」的文件知識庫助理。建議先使用一組可公開展示的技術文件或專案文件，清楚限定回答範圍。

已使用 Java、Spring Boot、Spring MVC、BM25、Vue 3、TypeScript 與本機 JSON 快照。Spring AI、模型、embedding 與向量儲存仍為後續選型，未列為已使用技術。

## 第一里程碑：離線檢索與可核對來源（已完成）

以下已有實作及測試紀錄：

- Markdown 匯入、段落切分、版本與 chunk ID，保存標題、行號及內容雜湊。
- 文件更新／刪除與 expectedVersion 衝突檢查；重建同 ID 仍遞增版本。
- BM25 檢索、文件範圍與中文雙字切詞；查詢時從目前快照建立詞彙索引。
- 原文摘錄、來源核對與後端產生的 canonical 引用；舊版本 409、已刪除文件 404。
- 空結果或詞彙涵蓋不足時沒有回答摘錄；涵蓋門檻不保證語意上足夠。
- 本機 JSON 原子替換、重啟持久化與 Vue 文件管理／問答／手機介面。
- 20 個後端測試與 4 個瀏覽器測試通過，前端型別檢查與 production build 成功。Ollama 3 個測試為 HTTP stub，503 畫面為模擬回應；正常瀏覽器流程與截圖使用真實後端。

作品集已放入六張真實截圖、概念架構、驗證紀錄與限制。本機 Demo 在 4175，後端為 18081，啟動方式依獨立專案 README。

## 固定評估與尚未解決的問題

條件：4 份自有文件、16 個段落、16 題，BM25、topK=5、詞彙涵蓋門檻 0.60。狀態符合預期 14/16、標註來源召回 13/14、原文及版本引用核對 49/49。引用有效不代表語意正確；小型自有題集不是獨立 holdout。

Q13 同義詞漏找；Q14 錯誤薪資前提混合其他關鍵字而通過門檻。這兩個失敗案例仍存在，沒有宣稱模型準確率、成本或檢索改善。

## 後續：真正的 RAG 與檢索品質（尚未完成）

- 驗證真實模型與 embedding，建立有來源約束的生成流程，與離線 baseline 明確區分。
- 比較不同切分大小與檢索參數；再評估關鍵字＋向量的混合檢索、reranking 是否值得加入。
- 記錄相關段落召回、引用正確性、回答是否受證據支持、無答案處理、回應時間與 token 用量。
- 保留相同資料版本、題集、模型及參數，使不同設定的結果可以公平比較。
- 保留既有惡意文件案例，後續另行驗證真實模型的處理行為；目前文字呈現及 stub 測試不代表真實模型安全性。

完成後展示：基準方法與改良方法的實測差異、失敗案例與技術取捨。未量測前不宣稱正確率、成本或效能改善。

## 第三階段：加入後端工程深度

以下依實際需求選擇，先完成第一階段再擴充：

- **權限感知的檢索**：由後端以已驗證的使用者身分決定可讀文件，在組裝模型上下文之前進行存取限制；以越權案例驗證隔離。
- **可追查的查詢流程**：使用 request ID 串起查詢、檢索文件版本、引用與模型結果，規劃記錄保留範圍。
- **受限的工具查詢**：先選一項唯讀功能，例如查詢公開商品資訊；由後端檢查權限與參數，再執行工具。

完成後才評估多步驟 agent 是否帶來實際價值；透過一致題集比較效果、延遲、成本與失敗處理。

## 作品集如何展示

展示頁已依第一里程碑補入以下內容；原始碼公開與真正的模型成果仍待補：

1. 使用情境、資料範圍與目前完成狀態。
2. 真實操作畫面、引用段落與無答案案例。
3. 文件匯入至回答產生的流程與資料模型。
4. 切分、檢索、引用與權限處理的技術決策。
5. 固定題集、測試結果、限制與失敗案例。
6. 可執行的原始碼、使用說明及 Demo 連結。

`features`、`challenges`、`architecture`、`verification` 與截圖已依第一里程碑的真實成果更新。`plannedGoals` 只保留尚未完成的模型／embedding、檢索改善與後續工程方向。

## 參考資料

- [Spring AI：Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)：RAG 流程、檢索 advisor 與 metadata filtering。
- [Spring AI：AI Concepts](https://docs.spring.io/spring-ai/reference/concepts.html)：文件切分、embedding 與 RAG 基本概念。
- [OWASP：RAG Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/RAG_Security_Cheat_Sheet.html)：文件、檢索與上下文處理的安全設計參考。
