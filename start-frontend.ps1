$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath "$PSScriptRoot/frontend"
node server.mjs
exit $LASTEXITCODE
