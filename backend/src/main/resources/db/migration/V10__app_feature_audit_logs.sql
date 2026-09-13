-- =====================================================================
-- V10__app_feature_audit_logs.sql
--
-- ホーム画面に「監査ログ」の入口を追加する（FR-J04）。監査ログは店舗単位ではなく
-- テナント全体を対象にするため requires_store = false。経営管理者のみ閲覧可
-- （AuditLogService.search がロールを検証するため、ここでは表示の出し分けのみ）。
-- =====================================================================

INSERT INTO app_feature (feature_key, title, description, path, display_order, requires_store, created_by) VALUES
    ('audit-logs', '監査ログ', '重要操作の履歴を検索・閲覧', '/audit-logs', 6, false, 'system');

INSERT INTO app_feature_role (app_feature_id, role)
SELECT id, 'OWNER' FROM app_feature WHERE feature_key = 'audit-logs';
