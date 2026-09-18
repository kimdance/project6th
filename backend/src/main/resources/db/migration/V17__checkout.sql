-- =====================================================================
-- V17__checkout.sql
--
-- 会計・レジ（FR-G01〜G07b・G10・G11）のアプリ層を実装するにあたり、店舗設定に
-- 「会計の取消・返金・値引きは店長承認を必須にするか」を追加する（FR-G10。
-- 02_requirements.md §3.2「会計の取消・返金・値引き｜◯｜◯｜△※」の※注記どおり、
-- ホールはこの設定次第で可否が変わる）。既定はfalse＝ホールも取消・返金・値引きができる。
--
-- guest_check／guest_check_line／guest_check_discount／guest_check_tax_line／payment／
-- refund の物理テーブルは V1__init_schema.sql の時点で作成済みのため、ここでは設定列のみ追加する。
-- =====================================================================

ALTER TABLE store_setting
    ADD COLUMN require_manager_approval_for_void_refund BOOLEAN NOT NULL DEFAULT false;
