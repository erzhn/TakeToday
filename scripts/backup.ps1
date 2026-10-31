# Резервная копия TakeToday (п. 5.3.1 ТЗ): база PostgreSQL из compose.yaml + загруженные фото и логотипы.
# Файл сохранён в UTF-8 с BOM: иначе Windows PowerShell 5 читает русский текст в неправильной кодировке.
#
#   .\scripts\backup.ps1                 — копия в папку backups\
#   .\scripts\backup.ps1 -Keep 30        — хранить 30 последних копий (по умолчанию 14)
#
# Сайт останавливать не нужно: pg_dump делает согласованный снимок работающей базы.
param(
    [int]$Keep = 14,
    [string]$Target = ''
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
if (-not $Target) { $Target = Join-Path $root 'backups' }

# DB_USER и UPLOAD_DIR — из .env, как в start-backend.ps1
$dbUser = 'taketoday'
$uploadDir = 'uploads'
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^DB_USER=(.+)$') { $dbUser = $Matches[1].Trim() }
        if ($line -match '^UPLOAD_DIR=(.+)$') { $uploadDir = $Matches[1].Trim() }
    }
}

New-Item -ItemType Directory -Force -Path $Target | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$dump = Join-Path $Target "taketoday-$stamp.dump"

# 1. База. Дамп пишется внутри контейнера и копируется файлом: через конвейер PowerShell 5 двоичный файл портится.
Write-Host "База -> $dump"
docker compose exec -T postgres pg_dump -U $dbUser -d taketoday --format=custom --file=/tmp/taketoday-backup.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_dump завершился с ошибкой. Запущена ли база (docker compose up -d)?' }
docker compose cp postgres:/tmp/taketoday-backup.dump $dump
if ($LASTEXITCODE -ne 0) { throw 'Не удалось скопировать дамп из контейнера.' }
docker compose exec -T postgres rm -f /tmp/taketoday-backup.dump | Out-Null
if ((Get-Item -LiteralPath $dump).Length -lt 1024) { throw "Дамп подозрительно маленький: $dump" }

# 2. Фото наборов и логотипы заведений
if (Test-Path -LiteralPath $uploadDir) {
    $zip = Join-Path $Target "uploads-$stamp.zip"
    Write-Host "Файлы -> $zip"
    Compress-Archive -Path (Join-Path $uploadDir '*') -DestinationPath $zip -Force
}

# 3. Старые копии удаляем, оставляем $Keep последних
foreach ($pattern in 'taketoday-*.dump', 'uploads-*.zip') {
    Get-ChildItem -LiteralPath $Target -Filter $pattern | Sort-Object Name -Descending |
        Select-Object -Skip $Keep | Remove-Item -Force
}

Write-Host "Готово. Восстановление: .\scripts\restore.ps1 -File `"$dump`""
