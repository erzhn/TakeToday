# Восстановление TakeToday из резервной копии (п. 5.3.1 ТЗ).
# Файл сохранён в UTF-8 с BOM: иначе Windows PowerShell 5 читает русский текст в неправильной кодировке.
#
#   .\scripts\restore.ps1 -File backups\taketoday-2026-12-20_03-00-00.dump [-Uploads backups\uploads-....zip]
#
# ВНИМАНИЕ: текущие данные базы заменяются данными из копии. Перед запуском остановите сайт (Ctrl+C в окне
# start-backend.ps1), после — запустите снова: Flyway доприменит миграции, если копия старее кода.
param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$Uploads = ''
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
if (-not (Test-Path -LiteralPath $File)) { throw "Нет файла $File" }

$dbUser = 'taketoday'
$uploadDir = 'uploads'
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^DB_USER=(.+)$') { $dbUser = $Matches[1].Trim() }
        if ($line -match '^UPLOAD_DIR=(.+)$') { $uploadDir = $Matches[1].Trim() }
    }
}

$answer = Read-Host "Данные базы taketoday будут заменены копией $File. Продолжить? (да/нет)"
if ($answer -ne 'да') { Write-Host 'Отменено.'; exit 1 }

docker compose cp $File postgres:/tmp/taketoday-restore.dump
if ($LASTEXITCODE -ne 0) { throw 'Не удалось скопировать файл в контейнер. Запущена ли база (docker compose up -d)?' }
docker compose exec -T postgres pg_restore -U $dbUser -d taketoday --clean --if-exists --no-owner --single-transaction /tmp/taketoday-restore.dump
$code = $LASTEXITCODE
docker compose exec -T postgres rm -f /tmp/taketoday-restore.dump | Out-Null
if ($code -ne 0) { throw 'pg_restore завершился с ошибкой — база осталась как была (одна транзакция).' }

if ($Uploads) {
    if (-not (Test-Path -LiteralPath $Uploads)) { throw "Нет файла $Uploads" }
    New-Item -ItemType Directory -Force -Path $uploadDir | Out-Null
    Expand-Archive -LiteralPath $Uploads -DestinationPath $uploadDir -Force
    Write-Host "Файлы восстановлены в $uploadDir"
}
Write-Host 'База восстановлена. Запустите сайт: .\start-backend.ps1'
