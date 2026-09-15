-- =====================================================================
-- V8__users_retired_status.sql
--
-- 退職（退会）処理（ユーザー管理画面）のため、users.status に RETIRED を追加する。
-- あわせて、招待制の廃止（2026-09-11改訂）でもう使わない INVITED も許可値から外す
-- （既存データに INVITED は無いことを確認済み）。
-- =====================================================================

ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'LOCKED', 'RETIRED'));
