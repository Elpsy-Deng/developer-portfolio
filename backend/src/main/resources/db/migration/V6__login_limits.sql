-- Dedicated durable admission counters. No IP, username, password or session token is stored.
CREATE TABLE admin_login_bucket (
    bucket_key varchar(64) PRIMARY KEY CHECK (bucket_key ~ '^[0-9a-f]{64}$'),
    window_start bigint NOT NULL CHECK (window_start >= 0),
    attempts integer NOT NULL CHECK (attempts BETWEEN 1 AND 10000)
);
CREATE INDEX admin_login_bucket_age ON admin_login_bucket(window_start);
REVOKE ALL ON admin_login_bucket FROM portfolio_reader, portfolio_writer;
GRANT SELECT, INSERT, UPDATE, DELETE ON admin_login_bucket TO portfolio_auth;

-- Update only the untouched platform-owned seed. No changes to source projects or edited content.
UPDATE project SET
  status_note='作品 CRUD、版本／稽核與驗證圖片上傳／安全回收、持久登入限流與 Session 絕對期限已加入；帳號治理與正式部署待完成。',
  duration='Phase 4D 登入保護 · 2026/10/13',
  features=features || '["PostgreSQL 原子／有界登入配額、429 手動倒數；30 分鐘閒置或 8 小時絕對 Session 期限。"]'::jsonb,
  planned_goals='["帳號治理／最後管理員保護、分類與技術治理。", "正式 HTTPS／可信代理部署、協調資料庫與圖片備份還原。"]'::jsonb,
  limitations='["圖片使用平台獨立本機 storage adapter；SeaweedFS 等服務尚未串接，帳號治理、可信代理與正式 HTTPS 仍待驗收。", "登入限流與絕對期限已加入；共享代理來源配額、匿名 Session 端點與正式容量仍須部署驗收。", "原始碼／證據可能需 GitHub 權限；公開 Demo、個人年資與正式經歷待補。"]'::jsonb,
  version=version+1,updated_at=clock_timestamp()
WHERE slug='developer-portfolio' AND version=4 AND duration='Phase 4C 圖片管理 · 2026/10/12'
  AND status_note='作品 CRUD、版本／稽核與驗證圖片上傳／安全回收已加入；身份治理與正式部署待完成。';
