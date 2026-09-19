-- =====================================================================
-- V19__floor_kitchen_access.sql
--
-- 小規模店舗ではホールとキッチンを兼務するスタッフがいるため、キッチンの権限を
-- ホールと同じにする（02_requirements.md §3.2、2026-09-19改訂）。対象は
-- 「注文管理」（卓のオープン／クローズ、注文の入力・数量変更・取消、会計の作成・
-- 確定・取消・返金・値引き）。バックエンドの権限チェックは
-- StoreAccessGuard#requireCanManageFloor で別途対応済みで、ここではホーム画面に
-- 「注文管理」の入口を表示するための app_feature_role のみ追加する。
-- =====================================================================

INSERT INTO app_feature_role (app_feature_id, role)
SELECT f.id, 'KITCHEN'
FROM app_feature f
WHERE f.feature_key = 'floor';
