# Limpa todos os dados da aplicacao no Postgres LOCAL do Docker (container ctrade-postgres) para recomecar
# uma rodada de testnet (T53). Mantem schema e historico do Flyway.
#
# Uso:  ./scripts/reset-testnet-db.ps1            (pede confirmacao)
#       ./scripts/reset-testnet-db.ps1 -Force     (sem confirmacao)
#
# Recusa rodar com a aplicacao no ar (porta 8080): ela guarda estado em memoria que ficaria inconsistente
# com o banco vazio. Pare o bootRun, rode este script e suba a aplicacao de novo.
param(
    [switch]$Force,
    [string]$Container = "ctrade-postgres",
    [string]$Database = "ctrade",
    [string]$User = "ctrade"
)

$ErrorActionPreference = "Stop"
$sqlFile = Join-Path $PSScriptRoot "reset_testnet_database.sql"

$appUp = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("localhost", 8080)
    $appUp = $tcp.Connected
    $tcp.Close()
} catch {
    $appUp = $false
}
if ($appUp) {
    Write-Error "A aplicacao parece estar rodando (localhost:8080). Pare o bootRun antes de limpar o banco."
    exit 1
}

$running = docker ps --filter "name=^$Container$" --format "{{.Names}}"
if ($running -ne $Container) {
    Write-Error "Container '$Container' nao esta rodando. Suba com: docker compose --profile base up -d"
    exit 1
}

if (-not $Force) {
    $answer = Read-Host "Apagar TODOS os dados de '$Database' no container '$Container'? Digite 'sim' para confirmar"
    if ($answer -ne "sim") {
        Write-Host "Cancelado."
        exit 0
    }
}

Get-Content -Raw $sqlFile | docker exec -i $Container psql -U $User -d $Database -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha ao limpar o banco (psql saiu com $LASTEXITCODE)."
    exit $LASTEXITCODE
}

$count = docker exec $Container psql -U $User -d $Database -t -A -c "SELECT COUNT(*) FROM portfolios"
Write-Host "Banco limpo. Portfolios restantes: $count"
