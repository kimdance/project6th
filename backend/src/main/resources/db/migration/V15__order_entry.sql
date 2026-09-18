-- =====================================================================
-- V15__order_entry.sql
--
-- 卓・注文（FR-E01〜E04・E07）のアプリ層を実装するにあたり、店舗設定に
-- 「提供後の注文明細の取消は店長承認を必須にするか」を追加する（FR-E03。
-- FR-G10の「会計の取消・返金は店舗設定で要店長承認にできる」と対になる、注文明細1行単位の設定）。
-- 既定はfalse＝ホールも提供後の取消ができる（従来の運用のまま）。
--
-- table_session／customer_order／order_line／kitchen_ticket 等の物理テーブルは
-- V1__init_schema.sql の時点で作成済みのため、ここではアプリ層に必要な設定列のみ追加する。
-- ホーム画面には「卓・注文」の入口を追加する（requires_store = true。経営管理者・店長・ホールに表示。
-- 02_requirements.md §3.2「卓のオープン／クローズ」「注文の入力・数量変更・取消」の権限どおり）。
-- =====================================================================

ALTER TABLE store_setting
    ADD COLUMN require_manager_approval_for_serve_cancel BOOLEAN NOT NULL DEFAULT false;

INSERT INTO app_feature (feature_key, title, description, path, display_order, requires_store, created_by) VALUES
    ('floor', '卓・注文', '卓のオープン、注文の入力・取消', '/floor', 9, true, 'system');

INSERT INTO app_feature_role (app_feature_id, role)
SELECT f.id, r.role
FROM app_feature f, UNNEST(ARRAY['OWNER', 'MANAGER', 'HALL']) AS r(role)
WHERE f.feature_key = 'floor';
