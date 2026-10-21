# 起動中のサーバー（既定 http://localhost:8081）へ API の正常系・異常系を打ち、応答と HTTP ステータスをそのまま出す。
# 出力はテスト実施記録（docs/08_プロジェクト管理/テスト実施記録.md）に貼る生の出力。
# ⛔ 副作用: 接続先の DB に申請を 1 件作って却下する。サーバーが equipment_lending を向いている時だけ打つ。
# 使い方: powershell -NoProfile -File scripts\check\api_checks.ps1 [-Base http://localhost:8081/api]
param([string]$Base = 'http://localhost:8081/api')

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# WHY: PowerShell 5.1 は外部コマンドへ渡す引数の中の " を落とす。JSON の本文はファイル経由で curl に渡す。
function Send-Body([string]$Method, [string]$Url, [string]$Body) {
  $tmp = New-TemporaryFile
  try {
    [System.IO.File]::WriteAllText($tmp, $Body, (New-Object System.Text.UTF8Encoding $false))
    curl.exe -s -w "`nhttp=%{http_code}`n" -X $Method -H 'Content-Type: application/json' --data-binary "@$tmp" $Url
  } finally {
    Remove-Item $tmp
  }
}

function Hit([string]$Label, [string]$Method, [string]$Url, [string]$Body) {
  "### $Label"
  "> $Method $Url $Body"
  if ($Body) { Send-Body $Method $Url $Body } else { curl.exe -s -w "`nhttp=%{http_code}`n" -X $Method $Url }
  ''
}

Hit '正常: 備品一覧' GET "$Base/equipment"
Hit '異常 400: 返却予定日が開始日より前' POST "$Base/requests" '{"applicantName":"API確認","equipmentId":1,"startDate":"2026-12-10","dueDate":"2026-12-01","reason":null}'
Hit '異常 400: 理由が 201 字' POST "$Base/requests" ('{"applicantName":"API確認","equipmentId":1,"startDate":"2026-12-01","dueDate":"2026-12-02","reason":"' + ('あ' * 201) + '"}')
Hit '異常 400: 存在しない状態で絞り込み' GET "$Base/requests?status=UNKNOWN"

$createBody = '{"applicantName":"API確認","equipmentId":2,"startDate":"2026-12-01","dueDate":"2026-12-02","reason":null}'
'### 正常: 申請（以降の 409 の対象）'
"> POST $Base/requests $createBody"
$created = Send-Body POST "$Base/requests" $createBody
$created
$json = ($created | Select-Object -First 1)
$id = ($json | ConvertFrom-Json).requestId
if (-not $id) { 'STOP: 申請を作れなかったので 409 の確認に進まない'; exit 1 }
''
Hit '異常 409: 申請中から返却' POST "$Base/requests/$id/return"
Hit '異常 409: 申請中から貸出' POST "$Base/requests/$id/lend"
Hit '正常: 却下' POST "$Base/requests/$id/reject"
Hit '異常 409: 却下の後に承認' POST "$Base/requests/$id/approve"
Hit '異常 404: 無い申請番号を承認' POST "$Base/requests/999999/approve"
Hit '異常 404: 無い申請番号を AI 確認' POST "$Base/requests/999999/ai-check"
