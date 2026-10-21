-- create_db.sql — 専用 DB `equipment_lending` と接続ユーザー `equipment_app` を作る（D-13）
-- 直接打たない。scripts/create_db.ps1 が psql の変数 app_pw を渡して実行する。
-- テーブルはここで作らない（作るのは backend の Flyway。WBS #9）。

\set ON_ERROR_STOP on

-- WHY: 手元には PostgreSQL 15・16・17 が同時に動いている。名前ではなく版とポートの値で
-- 接続先を確かめ、違えば何も作らずに止める（別の版に作る取り違えを防ぐ）。
SELECT (current_setting('server_version_num')::int / 10000 = 17
        AND current_setting('port') = '5433') AS is_target \gset
\if :is_target
\else
  \echo 'ABORT: target is not PostgreSQL 17 / port 5433. Nothing created.'
  \quit
\endif

-- WHY: 2 回目の実行で既存の DB やユーザーを上書きしない。どちらかがあれば止める。
SELECT NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'equipment_lending')
   AND NOT EXISTS (SELECT 1 FROM pg_roles    WHERE rolname = 'equipment_app') AS is_fresh \gset
\if :is_fresh
\else
  \echo 'ABORT: equipment_lending or equipment_app already exists. Nothing changed.'
  \quit
\endif

CREATE ROLE equipment_app LOGIN PASSWORD :'app_pw';
CREATE DATABASE equipment_lending OWNER equipment_app ENCODING 'UTF8' TEMPLATE template0;

-- 識別クエリ（WBS 8b の完了の定義）
SELECT current_setting('server_version') AS version,
       current_setting('port')           AS port,
       (SELECT datname FROM pg_database WHERE datname = 'equipment_lending') AS db,
       (SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'equipment_lending') AS owner;
