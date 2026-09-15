-- =====================================================================
-- V12__app_feature_reservations.sql
--
-- ホーム画面に「予約」の入口を追加する（FR-C01・C02）。予約台帳は店舗単位のため
-- requires_store = true。閲覧・登録・変更・キャンセルできるのは経営管理者・店長・ホール
-- （02_requirements.md §3.2「予約の登録・変更・キャンセル」。キッチン・バイトは対象外）。
-- =====================================================================

INSERT INTO app_feature (feature_key, title, description, path, display_order, requires_store, created_by) VALUES
    ('reservations', '予約', '予約の登録・変更・キャンセル', '/reservations', 7, true, 'system');

INSERT INTO app_feature_role (app_feature_id, role)
SELECT f.id, r.role
FROM app_feature f, UNNEST(ARRAY['OWNER', 'MANAGER', 'HALL']) AS r(role)
WHERE f.feature_key = 'reservations';
