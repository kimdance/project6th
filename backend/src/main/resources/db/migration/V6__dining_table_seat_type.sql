-- =====================================================================
-- V6__dining_table_seat_type.sql
--
-- 卓の「席種類」（カウンター／テーブル）を追加する。カウンター席は1席ずつ卓番号を分けて
-- 登録する運用のため、種類＝COUNTER の卓は seat_count が常に1になる（TableServiceで強制）。
-- 既存の卓はすべて通常のテーブル席として扱う（既定値 TABLE）。
-- =====================================================================

ALTER TABLE dining_table
    ADD COLUMN seat_type VARCHAR(20) NOT NULL DEFAULT 'TABLE'
        CHECK (seat_type IN ('COUNTER', 'TABLE'));
