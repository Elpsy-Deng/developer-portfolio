-- Dedicated runtime reads hashes only internally for proof/CAS; identity writer still cannot read/update them.
GRANT USAGE ON SCHEMA public TO portfolio_credentials;
GRANT SELECT(username,password_hash,role,enabled,version) ON admin_account TO portfolio_credentials;
GRANT UPDATE(password_hash,version) ON admin_account TO portfolio_credentials;
GRANT SELECT(principal_name),DELETE ON spring_session TO portfolio_credentials;
GRANT INSERT ON admin_identity_audit TO portfolio_credentials;
ALTER TABLE admin_identity_audit ALTER COLUMN action TYPE varchar(20);
ALTER TABLE admin_identity_audit DROP CONSTRAINT admin_identity_audit_action_check;
ALTER TABLE admin_identity_audit ADD CONSTRAINT admin_identity_audit_action_check CHECK(action IN ('CREATE','UPDATE','PASSWORD_CHANGE'));
UPDATE project SET
 status_note='作品 CRUD、驗證圖片上傳、持久登入限流、帳號治理與本人密碼更改已加入；忘記密碼重設與正式部署待完成。',
 duration='Phase 4F 本人密碼更改 · 2026/10/13',
 features=features || '["ADMIN／VIEWER 本人驗現有密碼後更改，持久限流、CAS／版本、同交易安全稽核與全 Session 撤銷。"]'::jsonb,
 planned_goals='["忘記密碼重設交付、分類／技術治理與分頁。", "正式 HTTPS／可信代理部署、協調資料庫與圖片備份還原。"]'::jsonb,
 limitations='["圖片使用平台獨立本機 storage adapter；SeaweedFS 尚未串接，忘記密碼重設、可信代理與正式 HTTPS 待驗收。", "帳號治理與最後管理員保護已加入；本人更改密碼不含代他人重設或Email交付。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
 version=version+1,updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=6 AND duration='Phase 4E 帳號治理 · 2026/10/13';
