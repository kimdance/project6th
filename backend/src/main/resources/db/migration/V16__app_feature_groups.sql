-- =====================================================================
-- V16__app_feature_groups.sql
--
-- ホーム画面の機能一覧を「管理業務」「店舗営業」の2グループに分けて表示できるよう、
-- app_feature に group_key を追加する。あわせて、卓・注文（floor）の表示名を
-- 「卓・注文」から「注文管理」に変更し、表示順をユーザー指定のグループ内順序に合わせて
-- 振り直す（グループ間で連番を続けるため display_order の値自体はまたがって一意のまま）。
-- =====================================================================

ALTER TABLE app_feature
    ADD COLUMN group_key VARCHAR(20) NOT NULL DEFAULT 'MANAGEMENT'
        CHECK (group_key IN ('MANAGEMENT', 'OPERATIONS'));

UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 1 WHERE feature_key = 'store';
UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 2 WHERE feature_key = 'business-days';
UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 3 WHERE feature_key = 'payment-methods';
UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 4 WHERE feature_key = 'tables';
UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 5 WHERE feature_key = 'users';
UPDATE app_feature SET group_key = 'MANAGEMENT', display_order = 6 WHERE feature_key = 'audit-logs';
UPDATE app_feature SET group_key = 'OPERATIONS', display_order = 7 WHERE feature_key = 'reservations';
UPDATE app_feature SET group_key = 'OPERATIONS', display_order = 8 WHERE feature_key = 'menu';
UPDATE app_feature SET group_key = 'OPERATIONS', display_order = 9, title = '注文管理' WHERE feature_key = 'floor';
