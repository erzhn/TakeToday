$ErrorActionPreference = 'Stop'
# Файл сохранён в UTF-8 с BOM: иначе Windows PowerShell 5 читает русский текст в неправильной кодировке.
Set-Location -LiteralPath $PSScriptRoot
$allowed = @('DB_URL','DB_USER','DB_PASSWORD','PORT','FRONTEND_ORIGIN','ADMIN_EMAIL','ADMIN_PASSWORD','UPLOAD_DIR',
             'MAIL_USERNAME','MAIL_PASSWORD','SPRING_MAIL_HOST','SPRING_MAIL_PORT','PAYMENT_MOCK_AVAILABLE','PAYMENT_MOCK_REFUNDS_AVAILABLE',
             'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH','SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE','MAIL_CONSOLE',
             'SPRING_PROFILES_ACTIVE','SSL_ENABLED','SSL_KEY_STORE','SSL_KEY_STORE_PASSWORD','SSL_KEY_ALIAS','SESSION_COOKIE_SECURE')
if (Test-Path -LiteralPath '.env') {
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^([A-Z_]+)=(.*)$' -and $allowed -contains $Matches[1]) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:DB_PASSWORD) { throw 'Укажите DB_PASSWORD в .env или переменных окружения.' }
# Профиль prod (показ на защите, https): нужна настоящая почта
if ($env:SPRING_PROFILES_ACTIVE -eq 'prod' -and -not $env:MAIL_USERNAME) {
    throw 'Для профиля prod укажите MAIL_USERNAME и MAIL_PASSWORD в .env (см. README, «Запуск по https»).'
}
if ($env:MAIL_CONSOLE -eq 'true') {
    Write-Host 'Почта: письма печатаются в консоль (MAIL_CONSOLE=true)'
}
elseif (-not $env:MAIL_USERNAME) {
    # Почта по умолчанию — локальный Mailpit из compose.yaml (письма видны на http://localhost:8025)
    $env:MAIL_USERNAME = 'noreply@taketoday.local'
    $env:MAIL_PASSWORD = 'local-unused'
    $env:SPRING_MAIL_HOST = 'localhost'
    $env:SPRING_MAIL_PORT = '1025'
    $env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH = 'false'
    $env:SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = 'false'
    Write-Host 'Почта: локальный Mailpit (http://localhost:8025)'
}
& "$PSScriptRoot/mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
