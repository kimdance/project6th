# 運営者向け操作手順

フェーズ1で運営者（自社）が行う操作の手順書。フェーズ2で運営者コンソールに置き換える予定。

---

## テナント作成（新しい会社を登録する）

新しい居酒屋の会社と契約したら、その会社と最初のオーナーアカウントを登録する。
公開のセルフサービス登録は行わない（運営者専用。`docs/02_requirements.md` FR-A02c、
`docs/04_architecture.md` §6.1）。

### 使うもの

- **Postman**（デスクトップアプリ。https://www.postman.com/downloads/ から入手）
- このフォルダの **`tenant-provisioning.postman_collection.json`**
- **合言葉**（サーバの設定 `app.operator.provision-token` に入っている値。運営責任者に確認）
- **バックエンドのアドレス**（開発中は `http://localhost:8080`、本番はデプロイ先）

### 準備（最初に1回）

1. Postman を起動し、左上 **Import** から `tenant-provisioning.postman_collection.json` を読み込む。
2. コレクション「project6th – テナント作成（運営者）」を選び、**Variables** タブを開く。
3. 次の2つの **Current value** を設定する（このファイルには保存しない＝合言葉をリポジトリに入れない）。
   - `baseUrl` … バックエンドのアドレス（例 `http://localhost:8080`）
   - `operatorToken` … 合言葉
4. 保存（Ctrl+S）。

> 合言葉は個人の環境にだけ置く。コレクションファイルを共有しても合言葉は含まれない。

### 登録のたびに行うこと

1. コレクション内のリクエスト **「テナント作成 POST /api/v1/admin/tenants」** を開く。
2. **Body** タブの5項目を今回の値に書き換える。
   | 項目 | 内容 | 制約 |
   |---|---|---|
   | `companyCode` | サブドメインに使う短い会社コード | 半角小文字の英数字とハイフン、3〜63文字。先頭/末尾/連続のハイフン不可。予約語（`www` `api` `admin` など）不可 |
   | `companyName` | 会社名（表示用） | 必須 |
   | `ownerName` | オーナーの氏名 | 必須 |
   | `ownerEmail` | オーナーのメールアドレス | 必須。ログインIDになる |
   | `password` | オーナーの初期パスワード | 8文字以上、英字と数字を両方含む |
3. **Send** を押す。
4. 右側のレスポンスを確認する。

   | ステータス | 意味 | 対応 |
   |---|---|---|
   | `201 Created` | 作成成功。本文に `{ "companyCode": "..." }` | オーナーへ「`<companyCode>.<サービスドメイン>` から、メールアドレスと初期パスワードでログイン」と案内 |
   | `400 Bad Request` | 入力エラー。本文 `errors[]` に項目ごとの内容 | 該当項目を直して再送 |
   | `403 Forbidden` | 合言葉が未設定/不一致 | `operatorToken` の値、サーバ側の設定を確認 |
   | `409 Conflict` | その `companyCode` は既に使われている | 別の `companyCode` にする |

### 補足

- `companyCode` と `ownerEmail` は小文字化して保存される（`Acme-Izakaya` → `acme-izakaya`）。
- パスワードは暗号化（ハッシュ化）して保存される。平文は保存されない。
- 作成されるオーナーは全店権限（`role = OWNER`、特定店舗に紐づかない）。店舗やスタッフはオーナーがログイン後に登録する。
