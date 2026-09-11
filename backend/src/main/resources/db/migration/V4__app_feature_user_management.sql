-- =====================================================================
-- V4__app_feature_user_management.sql
--
-- ホーム画面に「ユーザー管理」の入口を追加する。ユーザー登録画面（FR-A03）では役割・所属店舗を
-- 選ばせないため、店長・経営管理者への変更と店舗の割り当ては、経営管理者がこの画面から行う。
-- 店舗が無くても（新人を先に経営管理者へ昇格させたい等）使えるよう requires_store = false。
-- =====================================================================

INSERT INTO app_feature (feature_key, title, description, path, display_order, requires_store, created_by) VALUES
    ('users', 'ユーザー管理', 'スタッフの役割・所属店舗の変更', '/users', 5, false, 'system');

INSERT INTO app_feature_role (app_feature_id, role)
SELECT id, 'OWNER' FROM app_feature WHERE feature_key = 'users';
