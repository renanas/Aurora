# Cópia de segurança do banco do modo casa (dados reais de pacientes).
#
#   Na raiz do projeto:  .\scripts\aurora-casa-backup.ps1
#
# Grava backups\aurora-casa-AAAA-MM-DD-HHmm.dump (formato do pg_dump; a pasta fica fora do Git).
# Guarde uma cópia FORA deste computador (pen drive ou nuvem), de preferência num arquivo compactado
# com senha: o arquivo tem nomes e telefones de pacientes.
#
# Restaurar (apaga o que existe no banco do modo casa e põe a cópia no lugar; feche antes a janela do servidor):
#   $c = docker compose -f docker-compose.casa.yml --env-file .env.casa ps -q db
#   docker cp backups\ARQUIVO.dump "${c}:/tmp/restaurar.dump"
#   docker compose -f docker-compose.casa.yml --env-file .env.casa exec -T db pg_restore -U aurora_migrator -d aurora --clean --if-exists /tmp/restaurar.dump

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root '.env.casa'
if (-not (Test-Path $envFile)) { throw 'Nao existe .env.casa: o modo casa ainda nao foi ligado neste computador.' }

$dir = Join-Path $root 'backups'
New-Item -ItemType Directory -Force $dir | Out-Null
$file = Join-Path $dir ("aurora-casa-{0}.dump" -f (Get-Date -Format 'yyyy-MM-dd-HHmm'))

# O dump é gerado dentro do container e copiado depois: redirecionar a saída binária pelo PowerShell 5.1 a estragaria.
docker compose -f docker-compose.casa.yml --env-file $envFile exec -T db pg_dump -U aurora_migrator -d aurora -Fc -f /tmp/aurora.dump
if ($LASTEXITCODE -ne 0) { throw 'Falha no pg_dump. O banco do modo casa esta ligado?' }
$container = docker compose -f docker-compose.casa.yml --env-file $envFile ps -q db
docker cp "${container}:/tmp/aurora.dump" $file
if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar o arquivo do container.' }
docker compose -f docker-compose.casa.yml --env-file $envFile exec -T db rm /tmp/aurora.dump

Write-Host "Copia gravada em $file"
