# WBS — equipment-lending

⛔ 行が無い作業に着手しない。実施済みの正は git 履歴。ここは残作業・現在地・**作業の順番**を持つ。
順番は「依存」列で決まる。依存先が ✅ になるまで着手しない。

## 成果物分解（＝今回の手順）

| # | 成果物 | タスク | 依存 | 完了の定義（第三者が確かめられる形） | 状態 |
|---|---|---|---|---|---|
| 1 | 要件・業務フロー | 題材と状態を決める | — | `docs/02_要求/要件定義書.md`・`業務フロー図.md` があり、決定が `docs/01_意思決定/意思決定記録.md` D-2 にある | ✅ |
| 2 | ローカルのリポジトリ | `git init`（雛形 README §2 の起点） | 1 | `git -C <repo> rev-parse --is-inside-work-tree` が `true` | ✅ |
| 3 | 骨格の宣言 | プロファイル D・型 b を宣言 | 2 | `docs/03_構造/ディレクトリ構成定義.md` に「本体のソースは」の行がある | ✅ |
| 4 | 設定ファイル | `.gitignore`（Node・Maven の節）・`.gitattributes` | 2 | `.gitignore` に `node_modules/`・`target/`・`.env` がある（`backend/target` はビルド前は存在しないので check-ignore に出ない） | ✅ |
| 5 | 入口 | `CLAUDE.md`・`README.md` | 3 | 両方がリポ直下にあり、`CLAUDE.md` に禁止事項がある | ✅ |
| 6 | 最初のコミット | 雛形の取り込み確認→コミット | 3,4,5 | `git ls-files` に `.template` で終わるものが 0 件・`git status --porcelain` が 0 行 | ✅ |
| 7 | GitHub のリポジトリ | `gh repo create` で作成し push | 6 | `git log @{u}..HEAD` が 0 件 | ✅ |
| 7a | 公開 | 内部メモを除いて public にする（D-12） | 7 | `gh repo view --json visibility` が `PUBLIC`・履歴が 1 コミット | ✅ |
| 8 | 画面設計 | Figma に MCP で 1 画面を作る（**大前提・D-11**） | 7・Figma 認証 | Figma のファイル URL と画面のキャプチャを `docs/05_振る舞い/画面設計書.md` に載せる | ⏸ Figma 認証待ち |
| 8a | テーブル設計 | 専用 DB のテーブルを設計する（D-10） | 1 | `docs/04_契約/テーブル定義書.md` がある | ✅ |
| 8b | 専用 DB | PostgreSQL に `equipment_lending` を新しく作る | 8a・作り方の決定 | `psql` の識別クエリで DB 名・ポート・版を値で確かめる | ⏸ 決定待ち |
| 8c | Dify | Dify で業務の一部を AI に任せる（**大前提・D-11**。何を任せるかは決定待ち） | 8b | Dify のアプリ画面と、アプリから呼んだ結果を記録に残す | ⏸ 決定待ち |
| 9 | サーバー | Spring Boot の API（要件定義書 §7） | 6 | `mvn test`（`-q` なし）の `Tests run:` 行で失敗 0。正常系・異常系（400／404／409）を含む | ⬜ |
| 10 | 画面 | React（フォーム・一覧・絞り込み・状態ボタン） | 9（8 があれば合わせる） | `npm run build` が成功し、ブラウザで申請→承認→貸出→返却が通る | ⬜ |
| 11 | 動作確認 | 正常系と異常系を画面と API で確かめる | 9,10 | 結果を `docs/08_プロジェクト管理/テスト実施記録.md` に生の出力で残す | ⬜ |
| 12 | 説明資料 PDF | 流れ（Figma→Claude Code→React/Spring Boot→Dify）を説明する | 8,8c,11 | `docs/07_案内/業務模擬の説明.pdf` がある | ⬜ |

## 前提とバッファ

- Figma と Dify は外さない（D-11）。時間が足りない時に削るのは画面の作り込みとテストの件数の側。
- オーナーの操作が要るのは Figma の認証と、Dify の API キーの用意（AI は認証情報を扱わない）。
