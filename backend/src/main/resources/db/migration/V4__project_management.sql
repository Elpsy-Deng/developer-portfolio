CREATE TABLE project_audit (
  id uuid PRIMARY KEY,
  project_slug varchar(80) NOT NULL,
  actor varchar(64) NOT NULL,
  action varchar(12) NOT NULL CHECK(action IN ('CREATE','UPDATE','DELETE')),
  before_snapshot jsonb,
  after_snapshot jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK ((action='CREATE' AND before_snapshot IS NULL AND after_snapshot IS NOT NULL)
      OR (action='UPDATE' AND before_snapshot IS NOT NULL AND after_snapshot IS NOT NULL)
      OR (action='DELETE' AND before_snapshot IS NOT NULL AND after_snapshot IS NULL))
);
CREATE INDEX project_audit_slug_time ON project_audit(project_slug,created_at DESC,id);
REVOKE ALL ON project_audit FROM portfolio_reader, portfolio_auth;
GRANT USAGE ON SCHEMA public TO portfolio_writer;
GRANT SELECT ON category, project, technology, project_technology, project_media, project_evidence TO portfolio_writer;
GRANT INSERT, UPDATE, DELETE ON project, project_technology, project_evidence TO portfolio_writer;
GRANT DELETE ON project_media TO portfolio_writer;
GRANT INSERT ON technology TO portfolio_writer;
GRANT SELECT, INSERT ON project_audit TO portfolio_writer;
-- Verify authority/version without granting access to password hashes or identity writes.
GRANT SELECT(username,role,enabled,version) ON admin_account TO portfolio_writer;

-- Only the unedited platform milestone marker is advanced; original source works remain unchanged.
UPDATE project SET
  status_note='管理登入、作品 CRUD、草稿／發布、版本衝突與操作稽核已加入；圖片上傳與正式部署待完成。',
  duration='Phase 4B 作品管理 · 2026/10/12',
  features=features || '["管理作品新增／編輯／刪除／發布與排序；expectedVersion 衝突保護，稽核與修改同交易保存。"]'::jsonb,
  planned_goals='["圖片上傳與素材治理。", "帳號治理／登入限流、正式 HTTPS 部署與備份還原。"]'::jsonb,
  limitations='["作品 CRUD 與操作稽核已加入；圖片上傳、登入限流、帳號治理與正式 HTTPS 尚待驗收。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
  version=version+1, updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=2 AND duration='Phase 4A 管理登入與權限 · 2026/10/12';
