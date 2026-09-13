-- =====================================================================
-- V11__app_feature_audit_logs_manager.sql
--
-- 監査ログのホーム画面カードを店長（MANAGER）にも表示する。店長は自分の所属店舗に
-- 紐づく操作のみ閲覧できる（AuditLogService.search）。requires_store は変更しない
-- （店舗が0件の新規テナントでも経営管理者はカード自体は見える設計を維持するため）。
-- =====================================================================

INSERT INTO app_feature_role (app_feature_id, role)
SELECT id, 'MANAGER' FROM app_feature WHERE feature_key = 'audit-logs';
