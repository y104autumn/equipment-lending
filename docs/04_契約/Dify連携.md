# Dify 連携 — equipment-lending（外部 API の契約）

> 読者: `backend/` から Dify を呼ぶ部分を実装する人・AI エージェント
> 読むタイミング: WBS 8c に着手する前
> 何を任せるかの決定は `docs/01_意思決定/意思決定記録.md` D-14。ここは「どう呼ぶか」だけ。
> 出典はすべて Dify の公式ドキュメント（2026-10-21 に参照）。仕様が変わったら公式が正。

## 1. 呼び方

| 項目 | 内容 | 出典 |
|---|---|---|
| アプリの種類 | Workflow（呼ぶたびに独立して 1 回動き、会話の状態を持たない） | https://docs.dify.ai/en/api-reference/guides/workflow |
| エンドポイント | `POST https://api.dify.ai/v1/workflows/run`（Dify Cloud） | https://docs.dify.ai/en/api-reference/workflow-runs/run-workflow |
| 認証 | `Authorization: Bearer {API_KEY}`。キーはアプリごとに 1 本。サーバーからだけ呼ぶ | https://docs.dify.ai/en/api-reference/guides/get-started |
| 本文の必須項目 | `inputs`（ワークフローの入力変数）・`user`（呼び出し元の識別子）。`response_mode` は `blocking` を指定する | run-workflow（同上） |
| 結果の場所 | `data.outputs`。ワークフローに Output ノードが無いと何も返らない | run-workflow ／ https://docs.dify.ai/en/cloud/use-dify/nodes/output |
| タイムアウト | blocking は 100 秒まで（Cloudflare） | run-workflow（同上） |

## 2. このアプリでの入出力

| 方向 | 変数 | 中身 |
|---|---|---|
| 入力 | `equipment_name`・`start_date`・`due_date`・`reason` | 申請の内容（`テーブル定義書.md` の申請の列。`reason` は任意で空のことがある） |
| 出力 | `check_points` | 総務が承認前に確かめる点（文章）。承認するかは総務が決める（D-14） |

### 2.1 Dify 側のアプリ

| 項目 | 内容 |
|---|---|
| アプリ名 | 備品貸出 申請理由の確認（Workflow・公開済み） |
| 構成 | 開始（入力 4 つ）→ LLM（gpt-4.1-mini）→ 出力（`check_points`） |
| 設定の正 | [`dify/備品貸出_申請理由の確認.yml`](dify/備品貸出_申請理由の確認.yml)（Dify の「DSL をエクスポート」で 2026-10-22 に書き出したもの）。プロンプト本文・モデル（`gpt-4.1-mini-2025-04-14`・temperature 0.7）・ノードのつながりはこのファイルにある。Dify 側のアプリが消えたら、このファイルを Dify に読み込めば作り直せる |
| 画面 | [`dify/画面_ワークフロー.jpg`](dify/画面_ワークフロー.jpg)（LLM ノードを選んだ状態） |

⛔ Dify 側で設定を変えたら、書き出し直してこのファイルを差し替える（変えたまま放置すると、ここが正でなくなる）。書き出したファイルは、コミット前に全文を読んでキーが入っていないことを確かめる（2026-10-22 の版は `environment_variables: []`・キーの記載なし）。

![Dify のワークフロー画面](dify/画面_ワークフロー.jpg)

### 2.2 このアプリ側の呼び方（実装 `DifyClient.java`）

| 項目 | 内容 |
|---|---|
| 画面から呼ぶ API | `POST /api/requests/{id}/ai-check`。申請番号が無ければ 404。それ以外は Dify の成否にかかわらず 200 |
| 応答 | `{requestId, available, checkPoints, message}`。取れた時は `available=true` と `checkPoints`、取れなかった時は `available=false` と `message`＝「AI の確認を取得できませんでした」 |
| `user` | `request-{申請番号}`。申請者名は送らない（個人名を外部へ出さない） |
| `reason` が空 | 空文字で送る |
| タイムアウト | `dify.timeout-seconds`（既定 100 秒＝Dify の上限に合わせる） |
| キーが未設定 | Dify を呼ばずに「取得できない」を返す（テストはこの状態で回す） |
| 結果の保存 | しない（承認の補助・D-14） |

## 3. エラーの扱い

本文は `{"code": "...", "message": "...", "status": 400}` の 3 項目（https://docs.dify.ai/en/api-reference/guides/errors）。

| ステータス | 意味 | このアプリの扱い |
|---|---|---|
| 401 | キーが無い・誤り | 確認ポイントを出さず、画面に「AI の確認を取得できませんでした」を出す。申請・承認の操作は止めない |
| 400 | 入力の誤り・プランの上限 | 同上 |
| 429 `too_many_requests` | 同時実行が多い | 同上（再試行は今回は入れない） |
| 429 `rate_limit_error` | ワークスペースの実行枠を使い切った | 同上 |
| 500・タイムアウト | Dify 側の障害 | 同上 |

WHY: Dify は承認の補助であって業務の必須経路ではない。Dify が止まっても貸出の業務は続けられるようにする。

## 4. 設定と秘密情報

- API キーは `backend/.env` に `dify.api-key=...` として置く（git 管理外）。キーはオーナーが Dify の画面で発行する。
  - 発行する場所: Dify でアプリを開く → 左メニュー「アクセスポイント」 → 「API キー」 → 「新しいシークレットキーを作成」（`app-` で始まる。全文が見えるのは作成した時だけ）。
  - 置く場所は `backend/.env`。リポジトリ直下の `.env` は読まれない（`application.properties` の `spring.config.import=optional:file:.env[.properties]` は backend の作業ディレクトリから見る）。
  - 読まれたかの確かめ方: backend を起動し直して AI 確認を 1 回押す。ログに `dify.api-key が未設定のため Dify を呼ばない` が出なければ読まれている。
- 無料プラン（Sandbox）の AI クレジットは 200 で補充されない（https://docs.dify.ai/en/use-dify/getting-started/quick-start）。動作確認の呼び出し回数を絞る。

## 5. 手元の Dify（セルフホスト版・D-19・WBS 8d）

Dify Cloud のキーは外部サービスの本番の認証情報で AI が扱えないため、手元の PC に Dify を立てて LLM を Ollama にする（D-19）。

### 5.1 公式の要件（https://docs.dify.ai/en/self-host/quick-start/docker-compose・2026-10-22 に参照）

| 項目 | 公式の記載 |
|---|---|
| CPU / メモリ | 2 コア以上 / 4 GiB 以上 |
| Windows | Docker Desktop（WSL 2）・Docker Compose 2.24.0 以上 |
| 置き場所 | ソースとデータは Windows 側ではなく Linux（WSL）のファイルシステムに置く |
| 起動 | `docker compose up -d` → `http://localhost/install` で管理者を作る |

### 5.2 実施した手順（2026-10-22）

1. Docker Desktop を起動し、`docker info` が `29.4.0 / Docker Desktop` を返すまで待つ（約 4 秒）。
2. WSL Ubuntu の中で `docker` が使えることを確かめる（サーバー 29.4.0）。
3. 最新版を確かめる: GitHub API の `releases/latest` が `1.17.1`（2026-09-10 公開・prerelease でない）。
4. `git clone --depth 1 --branch 1.17.1 https://github.com/langgenius/dify.git`（WSL のホーム直下 `~/dify`）。
5. `cd ~/dify/docker && cp .env.example .env`（既定のまま。ポート 80／443 は空いていた）。
6. `docker compose up -d` → 終了コード 0。サービスは 15 個（api・worker・web・nginx・db_postgres・redis・weaviate・sandbox・plugin_daemon ほか）。

### 5.3 測った値

| 項目 | 値 | 測り方 |
|---|---|---|
| コンテナの合計メモリ | 約 1.95 GiB（api・api_websocket・worker が各 約 0.44 GiB） | `docker stats --no-stream` |
| WSL の VM | used 2.69 GB・buff/cache 4.90 GB（イメージ取得の名残）→ `drop_caches` 後 buff/cache 1.32 GB | `free -m` |
| Windows のコミット | Dify 起動前 40.67 / 50.85 GiB → 起動後 48.31 / 51.86 GiB | `Get-Counter '\Memory\Committed Bytes','\Memory\Commit Limit'` |
| Ollama のモデル（suzume-llama3・GPU に全部載る 5.28 GB） | 読み込み前 48.35 / 51.86 GiB → 読み込み後 **54.25 / 54.89 GiB**（余地 0.64 GiB）。応答まで 27.0 秒 | 同上・`/api/ps` |
| コンテナから Ollama | `http://host.docker.internal:11434/api/version` が `{"version":"0.40.6"}` を返す（Ollama が 127.0.0.1 待ち受けでも届く） | api コンテナ内の Python |

### 5.4 つまずいた点

- **メモリが足りない。** Dify とモデルを両方載せると、この PC ではコミットの余地が 1 GiB を切った（コミットの上限超えは PC ごと落ちる原因になる）。モデルを外すと 45.45 / 54.89 GiB に戻った。続けるには、他の常駐を減らすか、より小さいモデル（`llama3.2:3b`）にするかを決める必要がある。
- `drop_caches` は WSL の中の空きを増やすが、Windows 側のコミットはすぐには減らない。
- 起動直後の `api` は `unhealthy` で `/install` が 404 を返す（起動中）。管理者の作成はまだしていない。

### 5.5 未了（WBS 8d の残り）

管理者の作成 → Ollama プラグインの導入とモデル登録 → DSL の取り込みと LLM ノードの差し替え → 公開 → キー発行 → `curl` で `http://localhost/v1/workflows/run`。

止め方: `cd ~/dify/docker && docker compose down`（⛔ `-v` を付けない。データは `~/dify/docker/volumes/` に残る）→ Docker Desktop を終了 → `wsl --shutdown`。
