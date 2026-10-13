$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$allowed = @('DB_URL','DB_USER','DB_PASSWORD','PORT','FRONTEND_ORIGIN','SMTP_HOST','SMTP_PORT','SMTP_AUTH','SMTP_STARTTLS','SMTP_USER','SMTP_PASSWORD','MAIL_FROM','COOKIE_SECURE','ADMIN_EMAIL','ADMIN_PASSWORD')
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^([A-Z_]+)=(.*)$' -and $allowed -contains $Matches[1]) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:DB_PASSWORD) { throw 'Укажите DB_PASSWORD в .env или переменных окружения.' }
& "$PSScriptRoot/mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
