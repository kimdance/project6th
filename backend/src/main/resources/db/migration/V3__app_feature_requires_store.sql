-- =====================================================================
-- V3__app_feature_requires_store.sql
--
-- テナント登録直後（経営管理者は作成済みだが店舗が1件も無い）は、店舗を前提とする機能
-- （卓・決済手段・営業日）をホーム画面に出しても押せる先が無いため、「店舗設定」だけを
-- 表示できるようにする。store 非依存の app_feature（今は「店舗設定」のみ）は
-- requires_store = false のままにする。
-- =====================================================================

ALTER TABLE app_feature ADD COLUMN requires_store BOOLEAN NOT NULL DEFAULT false;

UPDATE app_feature
SET requires_store = true
WHERE feature_key IN ('tables', 'payment-methods', 'business-days');
