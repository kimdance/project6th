-- =====================================================================
-- V13__app_feature_menu.sql
--
-- ホーム画面に「メニュー管理」の入口を追加する（FR-D01〜D03）。メニューは店舗単位のため
-- requires_store = true。フルの編集（登録・価格変更等）は経営管理者・店長のみだが、
-- 売り切れ・提供停止の切替はホール・キッチンも行えるため（02_requirements.md §3.2）、
-- カード自体はその4ロールに表示する（バイトは対象外）。
-- =====================================================================

INSERT INTO app_feature (feature_key, title, description, path, display_order, requires_store, created_by) VALUES
    ('menu', 'メニュー管理', 'メニューの登録・編集・売り切れ切替', '/settings/menu', 8, true, 'system');

INSERT INTO app_feature_role (app_feature_id, role)
SELECT f.id, r.role
FROM app_feature f, UNNEST(ARRAY['OWNER', 'MANAGER', 'HALL', 'KITCHEN']) AS r(role)
WHERE f.feature_key = 'menu';
