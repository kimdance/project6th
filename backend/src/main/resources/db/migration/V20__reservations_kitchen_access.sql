-- =====================================================================
-- V20__reservations_kitchen_access.sql
--
-- V19に続き、小規模店舗ではホールとキッチンを兼務するスタッフがいるため、
-- 予約（FR-C01）についてもキッチンの権限をホールと同じにする
-- （02_requirements.md §3.2、2026-09-19改訂）。バックエンドの権限チェックは
-- StoreAccessGuard#requireCanManageReservations／ReservationService#list で
-- 別途対応済みで、ここではホーム画面に「予約」の入口を表示するための
-- app_feature_role のみ追加する。
-- =====================================================================

INSERT INTO app_feature_role (app_feature_id, role)
SELECT f.id, 'KITCHEN'
FROM app_feature f
WHERE f.feature_key = 'reservations';
