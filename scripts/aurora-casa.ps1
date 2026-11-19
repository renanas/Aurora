# Liga o Aurora no "modo casa": banco, servidor e telas, para usar no tablet pela rede de casa.
#
#   Na raiz do projeto:  .\scripts\aurora-casa.ps1
#
# O que faz:
#   1. Na primeira vez, cria o .env.casa com senhas aleatórias do banco (fica fora do Git).
#   2. Sobe o banco do modo casa (Docker, separado do banco de desenvolvimento).
#   3. Compila o servidor (Maven) e as telas (npm) e COPIA o resultado para a pasta .casa\ (fora do Git).
#      O modo casa roda dessa cópia: compilar o projeto no IntelliJ ou com "npm run build" durante o
#      desenvolvimento não mexe no que está ligado para ela.
#   4. Abre duas janelas: o servidor (Spring, porta 8090) e as telas (porta 4173, aberta para a rede).
#   5. Mostra o endereço para digitar no tablet.
# Para desligar: feche as duas janelas. O banco continua no Docker (desligar o Docker também serve).

param(
    # Pula a compilação e usa o que já foi compilado (liga mais rápido).
    [switch]$SemCompilar
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root '.env.casa'
$composeFile = Join-Path $root 'docker-compose.casa.yml'
$webPort = 4173
$apiPort = 8090

function New-RandomSecret([int]$length) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $length
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $chars = foreach ($b in $bytes) { $alphabet[$b % $alphabet.Length] }
    return -join $chars
}

function Find-Java25 {
    $candidates = @()
    if ($env:AURORA_JAVA_HOME) { $candidates += $env:AURORA_JAVA_HOME }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $jdks = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdks) {
        $candidates += Get-ChildItem $jdks -Directory | Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    }
    $candidates += Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }
    foreach ($jdkHome in $candidates) {
        $release = Join-Path $jdkHome 'release'
        if ((Test-Path $release) -and ((Get-Content $release -Raw) -match 'JAVA_VERSION="(\d+)')) {
            if ([int]$Matches[1] -ge 25) { return $jdkHome }
        }
    }
    throw 'Java 25 nao encontrado. Instale o JDK 25 ou defina AURORA_JAVA_HOME com a pasta dele.'
}

function Find-Maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    # Maven baixado pelo IntelliJ / Maven Wrapper
    $found = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Filter 'mvn.cmd' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($found) { return $found.FullName }
    throw 'Maven nao encontrado. Instale o Maven ou rode uma vez o projeto pelo IntelliJ.'
}

function Invoke-Sql([string]$sql) {
    $out = docker compose -f $composeFile --env-file $envFile exec -T db psql -U aurora_migrator -d aurora -tAc $sql
    return ($out | Out-String).Trim()
}

# ---------- 0. já está ligado? ----------
# Recompilar com o modo casa ligado trocaria os arquivos debaixo do servidor em uso.
$busyPorts = Get-NetTCPConnection -LocalPort $apiPort, $webPort -State Listen -ErrorAction SilentlyContinue
if ($busyPorts) {
    throw "O Aurora ja esta ligado (portas $apiPort/$webPort em uso). Feche as janelas 'Aurora - servidor' e 'Aurora - telas' e rode de novo."
}

# ---------- 1. senhas do banco ----------
if (-not (Test-Path $envFile)) {
    Write-Host 'Primeira execucao: criando .env.casa com senhas aleatorias do banco...'
    @(
        '# Senhas do banco do modo casa. Gerado por scripts/aurora-casa.ps1. NAO coloque no Git.',
        "AURORA_MIGRATOR_DB_PASSWORD=$(New-RandomSecret 32)",
        "AURORA_APP_DB_PASSWORD=$(New-RandomSecret 32)"
    ) | Set-Content $envFile -Encoding ascii
}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') {
        Set-Item -Path "env:$($Matches[1])" -Value $Matches[2]
    }
}

# ---------- 2. banco ----------
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'O Docker nao esta respondendo. Ligue o Docker Desktop e rode de novo.' }
docker compose -f $composeFile --env-file $envFile up -d
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel subir o banco do modo casa.' }

Write-Host 'Esperando o banco ficar pronto...'
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    docker compose -f $composeFile --env-file $envFile exec -T db pg_isready -U aurora_migrator -d aurora *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'O banco nao respondeu em 60 segundos.' }

# ---------- 3. compilar e copiar para .casa\ ----------
$env:JAVA_HOME = Find-Java25
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$casaDir = Join-Path $root '.casa'
$casaJar = Join-Path $casaDir 'aurora-api.jar'
$casaDist = Join-Path $casaDir 'dist'

if (-not $SemCompilar -or -not (Test-Path $casaJar) -or -not (Test-Path (Join-Path $casaDist 'index.html'))) {
    Write-Host 'Compilando o servidor (sem rodar os testes)...'
    & (Find-Maven) -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o servidor (mvn package).' }

    Write-Host 'Compilando as telas...'
    if (-not (Test-Path (Join-Path $root 'frontend\node_modules'))) {
        npm --prefix frontend ci
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar as dependencias do frontend (npm ci).' }
    }
    npm --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar as telas (npm run build).' }

    New-Item -ItemType Directory -Force $casaDir | Out-Null
    Copy-Item (Join-Path $root 'target\aurora-api-0.1.0-SNAPSHOT.jar') $casaJar -Force
    if (Test-Path $casaDist) { Remove-Item $casaDist -Recurse -Force }
    Copy-Item (Join-Path $root 'frontend\dist') $casaDist -Recurse
    $branch = (git -C $root rev-parse --abbrev-ref HEAD) 2>$null
    $commit = (git -C $root rev-parse --short HEAD) 2>$null
    "Compilado em $(Get-Date -Format 'yyyy-MM-dd HH:mm') a partir de $branch ($commit)" | Set-Content (Join-Path $casaDir 'versao.txt')
}
Write-Host (Get-Content (Join-Path $casaDir 'versao.txt') -ErrorAction SilentlyContinue)

# ---------- 4. conta do administrador (enquanto não existe nenhuma conta) ----------
# Só é usada se a conta ainda não existe; nunca troca a senha de uma conta existente.
$hasTable = Invoke-Sql "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = 'app_user'"
$hasAccounts = ($hasTable -eq '1') -and ((Invoke-Sql 'SELECT count(*) FROM app_user') -ne '0')
if (-not $hasAccounts -and -not $env:AURORA_AUTH_PASSWORD) {
    Write-Host ''
    Write-Host 'Conta do administrador (voce). Depois e so usar este e-mail e senha para entrar.'
    $env:AURORA_AUTH_USERNAME = Read-Host 'E-mail'
    $env:AURORA_AUTH_DISPLAY_NAME = Read-Host 'Seu nome'
    $secure = Read-Host 'Senha (12 caracteres ou mais)' -AsSecureString
    $env:AURORA_AUTH_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

# ---------- 5. ligar ----------
# As janelas herdam as variáveis deste processo (senhas do banco, Java e, na primeira vez, a conta do admin).
Start-Process powershell -WorkingDirectory $root -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'Aurora - servidor (nao feche)'; & '$java' -jar '$casaJar' --spring.profiles.active=casa"
)
$env:AURORA_API_URL = "http://127.0.0.1:$apiPort"
Start-Process powershell -WorkingDirectory (Join-Path $root 'frontend') -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'Aurora - telas (nao feche)'; npx vite preview --outDir '$casaDist' --host 0.0.0.0 --port $webPort --strictPort"
)
Remove-Item env:AURORA_AUTH_PASSWORD -ErrorAction SilentlyContinue

$ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Select-Object -ExpandProperty IPAddress

Write-Host ''
Write-Host 'Aurora ligando (o servidor leva uns 15 segundos).'
Write-Host "Neste computador:  http://localhost:$webPort"
foreach ($ip in $ips) { Write-Host "No tablet:         http://${ip}:$webPort" }
Write-Host ''
Write-Host 'O tablet precisa estar no mesmo Wi-Fi. Se o Windows perguntar sobre o firewall (Node.js),'
Write-Host 'permita em "Redes privadas". Deixe o computador ligado e sem hibernar enquanto ela usa.'
