-- Published V1/V2 are untouched. Accounts are provisioned explicitly, never seeded.
CREATE TABLE admin_account (
  username varchar(64) PRIMARY KEY CHECK (username ~ '^[a-z][a-z0-9_-]{2,63}$'),
  password_hash varchar(200) NOT NULL,
  role varchar(12) NOT NULL CHECK (role IN ('ADMIN','VIEWER')),
  enabled boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
-- Spring Session JDBC PostgreSQL schema; only the dedicated auth runtime writes it.
CREATE TABLE spring_session (
  primary_id char(36) PRIMARY KEY,
  session_id char(36) NOT NULL UNIQUE,
  creation_time bigint NOT NULL,
  last_access_time bigint NOT NULL,
  max_inactive_interval integer NOT NULL,
  expiry_time bigint NOT NULL,
  principal_name varchar(100)
);
CREATE INDEX spring_session_expiry_idx ON spring_session (expiry_time);
CREATE INDEX spring_session_principal_idx ON spring_session (principal_name);
CREATE TABLE spring_session_attributes (
  session_primary_id char(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
  attribute_name varchar(200) NOT NULL,
  attribute_bytes bytea NOT NULL,
  PRIMARY KEY(session_primary_id, attribute_name)
);
REVOKE ALL ON admin_account, spring_session, spring_session_attributes FROM portfolio_reader;
GRANT USAGE ON SCHEMA public TO portfolio_auth;
GRANT SELECT ON admin_account TO portfolio_auth;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes TO portfolio_auth;

-- Update only the untouched platform-owned Phase3 seed description. Edited rows are preserved.
UPDATE project SET
  status_note='管理登入、PostgreSQL Session 與角色權限已加入；作品 CRUD 與正式部署尚待實作。',
  duration='Phase 4A 管理登入與權限 · 2026/10/12',
  features=features || '["管理登入／登出、CSRF 與 PostgreSQL Session；管理員與唯讀檢視者由後端授權。"]'::jsonb,
  planned_goals='["作品 CRUD、版本／稽核與圖片管理。", "正式 HTTPS 部署、登入限流及帳號治理。"]'::jsonb,
  limitations='["公開唯讀 API 與管理登入已加入，尚無作品 CRUD；登入限流、帳號治理與正式 HTTPS 尚待驗收。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
  architecture=architecture || '["Spring Security／獨立 auth runtime／JDBC Session"]'::jsonb,
  version=version+1, updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=1 AND duration='Phase 3 PostgreSQL 保存 · 2026/10/12'
  AND status_note='公開查詢 API 已使用 PostgreSQL／Flyway，管理後台與正式部署尚未實作。';
