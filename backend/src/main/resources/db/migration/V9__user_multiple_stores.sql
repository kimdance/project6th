-- =====================================================================
-- V9__user_multiple_stores.sql
--
-- ユーザーの所属店舗を1つ（users.store_id）から複数（中間テーブル）に変更する。
-- 店長が複数店舗を兼任できるようにするための変更。ロールは全店舗共通のまま
-- （店舗ごとに別ロールは持たせない）。
-- =====================================================================

CREATE TABLE user_store (
    user_id  BIGINT NOT NULL REFERENCES users(id),
    store_id BIGINT NOT NULL REFERENCES store(id),
    PRIMARY KEY (user_id, store_id)
);

INSERT INTO user_store (user_id, store_id)
SELECT id, store_id FROM users WHERE store_id IS NOT NULL;

ALTER TABLE users DROP COLUMN store_id;
