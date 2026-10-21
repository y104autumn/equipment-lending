# create_db.ps1 — 専用 DB を作る（D-13・WBS 8b）。オーナーが 1 回だけ実行する。
#
# 使い方: powershell -ExecutionPolicy Bypass -File scripts\create_db.ps1
#   psql が postgres のパスワードを 1 回聞く。入力するのはオーナーだけ（AI は扱わない）。
#
# 何をするか:
#   1. 接続ユーザー equipment_app のパスワードを乱数で作る
#   2. create_db.sql を PostgreSQL 17（ポート 5433）に流す。版・ポートが違う、または既にあれば何もせず止まる
#   3. 成功した時だけ backend/.env に接続情報を書く（.env は .gitignore 済み＝git に入らない）

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$psql = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
$envFile = Join-Path $repo 'backend\.env'

# WHY: 既に .env があるなら DB も作成済みのはず。上書きするとパスワードが DB と食い違う。
if (Test-Path $envFile) { throw "中止: $envFile が既にある。何も変えていない。" }

# WHY: 英数字だけにする。記号は .env・psql・JDBC のどこかで引用の問題を起こしうる。
$chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
$bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$pw = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

# WHY: psql の出力は画面に出しつつ、成否の判定にも使う。
$out = & $psql -h localhost -p 5433 -U postgres -d postgres -v "app_pw=$pw" -f (Join-Path $PSScriptRoot 'create_db.sql') 2>&1
$out | ForEach-Object { "$_" }
if ($LASTEXITCODE -ne 0) { throw "psql が失敗した（exit=$LASTEXITCODE）。.env は書いていない。" }
# WHY: SQL 側の ABORT は \quit で抜けるため exit=0 になる。出力で見分ける。
if (($out | Out-String) -match 'ABORT') { throw '作成していない（上の ABORT を見る）。.env は書いていない。' }

# WHY: Spring Boot は application.properties の spring.config.import で .env をプロパティとして読む（WBS #9 で配線）。
$lines = @(
  'spring.datasource.url=jdbc:postgresql://localhost:5433/equipment_lending',
  'spring.datasource.username=equipment_app',
  "spring.datasource.password=$pw"
)
[IO.File]::WriteAllLines($envFile, $lines, (New-Object Text.UTF8Encoding $false))
"backend/.env を書いた（パスワードは表示しない）。"
