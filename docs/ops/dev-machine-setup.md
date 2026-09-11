# 開発環境セットアップ手順（新しいPC用）

project6th を新しいPCで開発できる状態にするための手順書。

> **このドキュメントの位置づけ**
> 2026-09時点ではまだ「実際に別PCへセットアップし直して動作確認した」わけではなく、
> 今使っている開発機（WSL2）での構成をもとに書き起こしたもの。
> 実際に新しいPCへセットアップする際は、この手順どおりに進めてみて、
> 詰まった箇所・手順が違った箇所をそのつどこのファイルに反映していくこと。
> 未検証の手順には `[未検証]` を付けている。

## 前提（今の開発機の構成）

- Windows + **WSL2（Ubuntu 24.04）** の中で全部動かす。Windows側に直接Node/PostgreSQLは入れない。
- バックエンド: Java 21（SDKMANで導入）+ Spring Boot（Maven、`mvnw` 同梱なのでMaven本体のインストールは不要）
- フロントエンド: Node.js（nvmで導入、`^20.19 || >=22.12` が必要。Vite 8のため）
- DB: PostgreSQL（WSL内、`localhost:5432`）
- コード管理: GitHub（`origin` = `https://github.com/kimdance/project6th.git`）

新しいPCでも同じ構成（WSL2の中に全部入れる）を前提にする。

---

## 全体の流れ

1. WSL2 + Ubuntu を用意する
2. GitHubからコードを取得する
3. Java・Maven回りを入れる（SDKMAN）
4. PostgreSQLを入れてDBを作る
5. Node.jsを入れる（nvm）
6. バックエンドを起動する
7. フロントエンドを起動する
8. （任意）パスワードリセットメールの確認用にMailpitを起動する
9. （任意）Windows側のGUIツールからDBを覗けるようにする

---

## 1. WSL2 + Ubuntu を用意する `[未検証]`

Windows側のPowerShell（管理者）で実行する。

```powershell
wsl --install -d Ubuntu-24.04
```

再起動後、Ubuntuのユーザー名・パスワードを設定して初回ログインまで済ませておく。

---

## 2. GitHubからコードを取得する

1. 新しいPCのGitHubアカウントで、このリポジトリ（`kimdance/project6th`）にアクセスできる状態にする。
   - リポジトリが非公開（プライベート）の場合、GitHubへのSSH鍵の登録、または `gh auth login` などでの認証が必要。
2. WSLのターミナルで clone する。

   ```bash
   git clone https://github.com/kimdance/project6th.git
   cd project6th
   ```

3. `git config user.name` / `git config user.email` がこのPCで未設定なら設定する（コミットの作者名に使われる）。

---

## 3. Java・Maven回りを入れる（SDKMAN）`[未検証]`

このプロジェクトは Java 21 を使う（`backend/pom.xml` の `java.version`）。
今の開発機では SDKMAN 経由で入れている。

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-tem
```

> `curl | bash` はこのハーネス（Claude Code）からは実行できないため、実施するときは自分のWSLターミナルで実行すること。

Maven自体は `backend/mvnw`（Maven Wrapper）が同梱されているので、別途インストールしなくてよい。

---

## 4. PostgreSQLを入れてDBを作る `[未検証]`

```bash
sudo apt update
sudo apt install postgresql
sudo service postgresql start
```

`backend/src/main/resources/application.properties` の接続設定に合わせて、
ユーザー `postgres` のパスワードを `postgres` にし、DB `ShopSystemDB` を作る。

```bash
sudo -u postgres psql -c "ALTER USER postgres WITH PASSWORD 'postgres';"
sudo -u postgres createdb ShopSystemDB
```

テーブルは自動で作られない。バックエンドを起動すると Flyway が `backend/src/main/resources/db/migration` のマイグレーションを自動適用してテーブルを作る（`spring.jpa.hibernate.ddl-auto=validate` なのでHibernateはテーブルを作らない）。

WSLを再起動するたびに `sudo service postgresql start` が必要（自動起動ではない）。

---

## 5. Node.jsを入れる（nvm）`[未検証]`

WSLにはNode.jsが最初から入っていない。Windows側のNodeは使えない
（`node_modules` の中身がWindows専用になり、Linux上のNodeでは動かないため）。

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
# シェルを開き直すか、以下を実行
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

nvm install 22
nvm use 22
```

> ここも `curl | bash` を含むので、実施するときは自分のWSLターミナルで実行すること。
> nvmのバージョン（`v0.40.1`）は目安。[nvmのリリースページ](https://github.com/nvm-sh/nvm/releases)で最新版を確認してよい。

---

## 6. バックエンドを起動する `[未検証]`

```bash
cd project6th/backend
./mvnw spring-boot:run
```

`http://localhost:8080` で起動する。起動時のログでFlywayのマイグレーションが流れることを確認する。

---

## 7. フロントエンドを起動する `[未検証]`

```bash
cd project6th/frontend
npm install
npm run dev
```

`http://localhost:5173` で起動する。WSL2のlocalhostフォワーディングにより、Windows側のブラウザからそのままアクセスできる。

このプロジェクトはテナント（会社）をサブドメインで識別する設計（`<company_code>.localhost`）なので、
動作確認は `http://localhost:5173` ではなく、たとえば `http://acme-izakaya.localhost:5173` のように
サブドメイン付きでアクセスする（`docs/04_architecture.md` §6.1、`frontend/.env` のコメントも参照）。
テナント自体がまだ無い場合は、先に `docs/ops/README.md` の「テナント作成」手順で1件作る。

---

## 8. （任意）パスワードリセットメールの確認用にMailpitを起動する `[未検証]`

パスワードリセット機能（FR-A04）は開発中、実メール送信の代わりに Mailpit を使う設定になっている
（`application.properties` の `spring.mail.host=localhost` / `port=1025`）。

```bash
docker run -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Dockerが未導入なら、まずWSL2にDocker（または Docker Desktop のWSL統合）を入れる必要がある。
送信されたメールは `http://localhost:8025` で認証不要で確認できる。

---

## 9. （任意）Windows側のGUIツールからDBを覗けるようにする `[未検証]`

開発機では A5:SQL Mk-2（Windows用のDBクライアント）を使っている。
WindowsからはWSL内の `localhost:5432` に直接繋がらないことがあるため、以下が必要になる場合がある。

1. WSL内で `ip -4 addr show eth0` を実行し、WSLのIPアドレスを確認する。
2. PostgreSQLの設定（`postgresql.conf`）で `listen_addresses = '*'` にする。
3. `pg_hba.conf` に `172.16.0.0/12` からの接続を許可する行を追加する。
4. A5:SQL Mk-2 側で、`localhost` ではなく手順1で確認したIPアドレスへの接続を新規作成する。

WSLを再起動するとIPアドレスが変わることがあるため、繋がらなくなったら手順1からやり直す。

---

## 共有されないもの（意図的に除外）

`.gitignore` により、以下は新しいPCには自動で来ない。これは想定通りで、問題ない。

- `.claude/settings.local.json`（Claude Codeの個人環境ごとの設定）
- `.claude/worktrees/`

---

## 更新履歴

- 2026-09-12: 初版作成（現行の開発機の構成から書き起こし。他PCでの実施はまだ）
