CREATE TABLE image_asset (
  id uuid PRIMARY KEY,
  format varchar(4) NOT NULL CHECK(format IN ('png','jpg')),
  mime varchar(20) NOT NULL CHECK ((format='png' AND mime='image/png') OR (format='jpg' AND mime='image/jpeg')),
  width integer NOT NULL CHECK(width BETWEEN 1 AND 4096),
  height integer NOT NULL CHECK(height BETWEEN 1 AND 4096),
  bytes bigint NOT NULL CHECK(bytes BETWEEN 1 AND 10485760),
  sha256 char(64) NOT NULL CHECK(sha256 ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  retired_at timestamptz,
  purged_at timestamptz,
  CHECK(width::bigint * height <= 8000000),
  CHECK(purged_at IS NULL OR retired_at IS NOT NULL)
);
ALTER TABLE project_media ADD COLUMN asset_id uuid REFERENCES image_asset(id);
ALTER TABLE project_media ADD CONSTRAINT managed_media_src CHECK(asset_id IS NULL OR src='/api/public/media/'||asset_id::text);
CREATE INDEX project_media_asset ON project_media(asset_id) WHERE asset_id IS NOT NULL;
CREATE INDEX image_asset_retired ON image_asset(retired_at) WHERE retired_at IS NOT NULL AND purged_at IS NULL;
GRANT SELECT ON image_asset TO portfolio_reader,portfolio_writer;
GRANT INSERT,UPDATE ON image_asset TO portfolio_writer;
GRANT INSERT,UPDATE ON project_media TO portfolio_writer;
-- Existing audit action vocabulary stays UPDATE; full snapshots capture the media change.
UPDATE project SET
  status_note='作品 CRUD、版本／稽核與驗證圖片上傳／安全回收已加入；身份治理與正式部署待完成。',
  duration='Phase 4C 圖片管理 · 2026/10/12',
  features=features || '["PNG／JPEG 真實解碼與重編碼、圖片版本／稽核、發布狀態存取與安全回收。"]'::jsonb,
  planned_goals='["帳號治理／登入限流、分類與技術治理。", "正式 HTTPS 部署、協調資料庫與圖片備份還原。"]'::jsonb,
  limitations='["圖片使用平台獨立本機 storage adapter；SeaweedFS 等服務尚未串接，登入限流、帳號治理與正式 HTTPS 仍待驗收。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
  version=version+1,updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=3 AND duration='Phase 4B 作品管理 · 2026/10/12';
