CREATE TABLE admin_identity_audit (
  id uuid PRIMARY KEY,
  username varchar(64) NOT NULL,
  actor varchar(64) NOT NULL,
  action varchar(8) NOT NULL CHECK(action IN ('CREATE','UPDATE')),
  before_state jsonb,
  after_state jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK(before_state IS NULL OR (jsonb_typeof(before_state)='object' AND before_state - ARRAY['username','role','enabled','version']::text[] = '{}'::jsonb)),
  CHECK(jsonb_typeof(after_state)='object' AND after_state - ARRAY['username','role','enabled','version']::text[] = '{}'::jsonb)
);
CREATE INDEX admin_identity_audit_recent ON admin_identity_audit(created_at DESC,id);
REVOKE ALL ON admin_identity_audit FROM portfolio_reader,portfolio_auth,portfolio_writer;
GRANT USAGE ON SCHEMA public TO portfolio_identity;
GRANT SELECT(username,role,enabled,version,created_at) ON admin_account TO portfolio_identity;
GRANT INSERT(username,password_hash,role,enabled) ON admin_account TO portfolio_identity;
GRANT UPDATE(role,enabled,version) ON admin_account TO portfolio_identity;
GRANT SELECT,INSERT ON admin_identity_audit TO portfolio_identity;
GRANT SELECT(principal_name),DELETE ON spring_session TO portfolio_identity;
-- Only untouched platform seed; never overwrite an edited project or source works.
UPDATE project SET
 status_note='作品 CRUD、驗證圖片上傳、持久登入限流與 Session 絕對期限、帳號／角色治理與最後管理員保護已加入；密碼重設與正式部署待完成。',
 duration='Phase 4E 帳號治理 · 2026/10/13',
 features=features || '["ADMIN 限定帳號建立／角色／停用、版本衝突、最後管理員保護、同交易安全稽核與 Session 撤銷。"]'::jsonb,
 planned_goals='["密碼重設交付、分類／技術治理與分頁。", "正式 HTTPS／可信代理部署、協調資料庫與圖片備份還原。"]'::jsonb,
 limitations='["圖片使用平台獨立本機 storage adapter；SeaweedFS 等服務尚未串接，密碼重設、可信代理與正式 HTTPS 仍待驗收。", "帳號治理與最後管理員保護已加入；獨立身份writer、目前最多100帳號及最近50筆稽核，正式容量與匿名Session防護仍待部署驗收。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
 version=version+1,updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=5 AND duration='Phase 4D 登入保護 · 2026/10/13';
