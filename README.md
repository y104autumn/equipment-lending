# equipment-lending — 備品貸出申請（業務模擬）

社員が備品を申請し、総務が承認・貸出・返却を記録する小さな WEB アプリ。

| 層 | 技術 |
|---|---|
| 画面設計 | Figma（Claude Code から MCP で操作） |
| 画面 | React＋TypeScript（Vite）— `frontend/` |
| サーバー | Spring Boot（Java 21・Maven）— `backend/` |
| DB | PostgreSQL（専用 DB `equipment_lending`） |
| AI | Dify |

設計書は `docs/`。作業の順番は `docs/08_プロジェクト管理/WBS.md`。
