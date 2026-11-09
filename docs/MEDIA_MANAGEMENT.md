# 圖片與素材治理（Phase 4C）

圖片管理入口 `/admin/projects/{slug}/media`。ADMIN 可上傳、編輯說明／替代文字／排序、移除及回收；VIEWER 可看草稿預覽／說明，不能寫入。操作沿用[作品版本與稽核](ADMIN_PROJECTS.md)，圖片與文字使用不同頁面，避免圖片操作取代尚未儲存的作品表單。

本輪使用免費 `ImageStore`／`LocalImageStore` adapter，獨立私有目錄，不串接其他作品storage。SeaweedFS／S3 adapter、正式對外及協調備份尚未驗收。

## 上傳與存取

- 僅PNG／JPEG，multipart原始檔 **5MiB**、整個請求6MiB。MIME與signature須相符，再由ImageIO實際解析尺寸及像素；最長邊4096px、最多800萬像素。SVG／GIF／WebP不接受。
- 解碼後重新輸出，拒絕損毀或重編碼後超過10MiB的圖片。original filename不作路徑，key是server UUID＋png/jpg；不保存尾部附加內容。JPEG重新壓縮可能影響品質，EXIF不保留；帶旋轉EXIF的照片需先轉正。不是病毒掃描或任意格式服務。
- 封面取代目前封面；截圖最多20張、追加到最後。title／description／alt上限160／5000／300，截圖title及真圖alt不得空白；排序不可重複，cover只能0。未做拖曳排序。
- 新圖片src `/api/public/media/{uuid}` 只服務目前被 **published作品引用、未退役** 的圖片；draft或移除後404 IMAGE_NOT_FOUND。管理預覽 `/api/admin/media/{uuid}` 需ADMIN／VIEWER與目前引用，沒有公開檔案目錄。
- 每次讀取先查PG發布／引用，再核對file bytes／SHA256；no-store、固定image MIME、nosniff及UUID filename。缺失／受損／symlink檔案503 IMAGE_STORAGE_UNAVAILABLE；PG故障503，不能繞過PG直送圖片。
- 原靜態素材／占位不自動遷移。URL仍由前端public目錄提供，歷史圖原本即可公開讀取；移除僅解除關聯，不刪Git檔案。發布限制只適用本輪managed images。

## API

管理寫入需目前Session的X-CSRF-TOKEN。成功回傳 `{project,version,published,sortOrder}`，aggregate version+1與UPDATE audit同交易，full snapshots含媒體變動。不接受client path、asset id或actor。

| 方法／路徑 | 契約 |
| --- | --- |
| GET `/api/admin/projects/{slug}/media` | id/kind/position/title/description/src/alt/managed/width/height/bytes；legacy尺寸null |
| POST `/api/admin/projects/{slug}/media` | multipart file、expectedVersion、kind(COVER/SCREENSHOT)、title、description、alt；200 |
| PUT `/api/admin/projects/{slug}/media/{id}` | JSON expectedVersion、title、description、alt、position；200，保留bytes/src |
| DELETE `/api/admin/projects/{slug}/media/{id}` | JSON expectedVersion；200，回傳新aggregate |
| POST `/api/admin/media/cleanup` | ADMIN，回傳本輪retired／orphaned／temporary數 |
| GET `/api/public/media/{uuid}` | 目前public引用的bytes，不符合404 |
| GET `/api/admin/media/{uuid}` | 管理身份預覽目前引用的bytes |

400 INVALID_IMAGE／INVALID_MEDIA／MEDIA_LIMIT，413 IMAGE_TOO_LARGE，429 IMAGE_BUSY，404 MEDIA_NOT_FOUND，409 VERSION_CONFLICT／MEDIA_ORDER_CONFLICT；圖片儲存503 IMAGE_STORAGE_UNAVAILABLE，DB／Session仍用既有503契約。不公開实际目錄、SQL或raw exception。

UI不自動重送寫入。409保留輸入，明確重載才取代說明；網路中斷先核對，不能把未收到回覆當成未保存。上傳成功但list重讀失敗會告知並清除已送file選擇。

## 儲存、交易與回收

`PORTFOLIO_MEDIA_ROOT`首次只能空目錄，建立 `.portfolio-media-v1` marker與objects／incoming；既有root須具正確marker。Unix目錄700、檔案600，拒絕root／ancestor／object symlink；只接受UUID與固定format，不用原filename、任意relative path或public目錄。

- canonical bytes先寫UUID.part、fsync檔案、同root atomic rename，再於writer PG交易存image_asset／project_media、version與audit；未commit圖片沒有DB讀取引用。任一步失敗rollback並嘗試刪新file，舊cover／version／audit保留。
- 檔案與PG不是跨資源原子交易。程序中斷／rollback IO失敗可留未登記object；**24小時grace**後，只掃自己objects的UUID.png/jpg且PG完全無asset row才刪除。incoming舊暫存也有24小時grace，symlink跳過、不跟隨到外部。
- 移除、cover替換或刪作品時，同交易將無任何引用的asset標retired_at，API立即404。cleanup僅刪無引用且retired的bytes，再記purged_at；asset metadata及audit保留。歷史src可404，audit不另存bytes副本。
- 每分鐘排程或ADMIN手動回收；每輪最多50 retired、50孤兒、50暫存。先刪file後記purged_at，DB／IO故障保持可重試，file已不存在可idempotent成功；被引用file不作孤兒刪除。低流量後台，未宣稱高併發object服務。
- 排程故障保留queue再試，目前無告警／失敗計數面板；手動cleanup能取得503。總容量／上傳限流、GC觀測、格式擴充另待做。

## 啟動與升級

新helper init產生 `PORTFOLIO_MEDIA_ROOT=<自有scope>/media`，與其他作品隔離。Phase4B scope啟動含V5 JAR前：

```bash
python scripts/portfolio_db.py enable-media --directory work/<原有scope>
```

只為缺設定的private env追加自有root，不轉檔／reset密碼；已有不同root拒絕，先保留核對。V1–V4不改。此adapter不新增Docker服務。

正常PG down保留volume和media目录；`--purge`只刪自有PG volume，不自動刪media或raw驗收，以免誤刪備份。驗收另證明自己的合成files已回收，scope不可提交。正式環境須固定持久root、保護ACL並協調PG／images／metadata備份及隔離restore；單獨DB restore不算圖片還原。static素材仍隨Git/dist交付。

## 驗證與後續

真PG／HTTP驗證PNG／JPEG、偽造／尺寸／5MiB／CSRF／viewer、draft／published、版本／metadata／cover、audit與IO rollback、GC retry／grace／symlink／hash。JAR／PG restart核對相同bytes與引用，PG停機不送bytes；一般browser503仍明確stub，正常及偽造PNG走真服務。

下一里程碑建議登入限流／帳號治理／Session絕對期限，再分類／技術治理／分頁、正式部署、storage adapter擴充與協調備份。尚未公開對外，不把來源作品歷史測試算成本輪。
