ALTER TABLE category ADD COLUMN version bigint NOT NULL DEFAULT 1 CHECK(version>0);
ALTER TABLE technology ADD COLUMN version bigint NOT NULL DEFAULT 1 CHECK(version>0);
CREATE TABLE taxonomy_audit (
 id uuid PRIMARY KEY,
 kind varchar(12) NOT NULL CHECK(kind IN ('categories','technologies')),
 term_id uuid NOT NULL,
 actor varchar(64) NOT NULL,
 action varchar(8) NOT NULL CHECK(action IN ('CREATE','UPDATE','DELETE')),
 before_state jsonb,
 after_state jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK((action='CREATE' AND before_state IS NULL AND after_state IS NOT NULL)
    OR (action='UPDATE' AND before_state IS NOT NULL AND after_state IS NOT NULL)
    OR (action='DELETE' AND before_state IS NOT NULL AND after_state IS NULL))
);
CREATE INDEX taxonomy_audit_recent ON taxonomy_audit(created_at DESC,id);
REVOKE ALL ON taxonomy_audit FROM portfolio_reader,portfolio_auth,portfolio_identity,portfolio_credentials;
GRANT SELECT,INSERT ON taxonomy_audit TO portfolio_writer;
GRANT INSERT,UPDATE,DELETE ON category TO portfolio_writer;
GRANT UPDATE,DELETE ON technology TO portfolio_writer;
UPDATE project SET
 status_note='作品 CRUD、驗證圖片上傳、持久登入限流、帳號治理、本人密碼更改與分類／技術治理及分頁已加入；正式部署待完成。',
 duration='Phase 4G 分類／技術與分頁 · 2026/10/13',
 features=features || '["動態分類／技術治理、expectedVersion／引用保護與稽核；改名連帶作品版本，公開／管理列表穩定分頁。"]'::jsonb,
 planned_goals='["忘記密碼重設交付、稽核導出與排序操作改善。", "正式 HTTPS／可信代理部署、協調資料庫與圖片備份還原。"]'::jsonb,
 version=version+1,updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=7 AND duration='Phase 4F 本人密碼更改 · 2026/10/13';
