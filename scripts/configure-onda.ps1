<#
.SYNOPSIS
Configura el Client ID de Spotify de Onda en local.properties y en los GitHub Secrets.

.DESCRIPTION
No muestra el valor por pantalla. Ejecútalo desde cualquier directorio:

    .\scripts\configure-onda.ps1

Opciones:
    -SkipGithub   No sube los secrets (solo escribe local.properties).
    -Build        Tras configurar, compila el release y verifica la firma.
#>
param(
    [switch]$SkipGithub,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $root 'local.properties'

Write-Host ''
Write-Host '=== Configuración de Spotify para Onda ===' -ForegroundColor Cyan
Write-Host 'Crea la app en https://developer.spotify.com/dashboard y añade el Redirect URI'
Write-Host 'ondaoauth://spotify/callback antes de continuar.'
Write-Host ''

$spotifyClientId = (Read-Host -Prompt 'Spotify Client ID').Trim()
$spotifyRedirectUri = (Read-Host -Prompt 'Spotify Redirect URI [ondaoauth://spotify/callback]').Trim()
if ([string]::IsNullOrWhiteSpace($spotifyRedirectUri)) {
    $spotifyRedirectUri = 'ondaoauth://spotify/callback'
}
if ([string]::IsNullOrWhiteSpace($spotifyClientId)) {
    throw 'El Spotify Client ID no puede estar vacío.'
}

$values = [ordered]@{
    'onda.spotify.clientId'    = $spotifyClientId
    'onda.spotify.redirectUri' = $spotifyRedirectUri
}

# ---------- local.properties ----------
$preserved = @()
if (Test-Path $localProperties) {
    $preserved = Get-Content $localProperties | Where-Object {
        $_ -match '^(sdk\.dir|onda\.storePassword|onda\.keyPassword|onda\.keyAlias)='
    }
} else {
    $preserved += 'sdk.dir=C\:\\Users\\CARLOS ERNESTO\\AppData\\Local\\Android\\Sdk'
}

$lines = @($preserved)
foreach ($key in $values.Keys) {
    $lines += "$key=$($values[$key])"
}
$lines | Set-Content -Path $localProperties -Encoding UTF8
Write-Host "OK  local.properties actualizado" -ForegroundColor Green

# ---------- GitHub Secrets ----------
if (-not $SkipGithub) {
    try {
        gh api user --jq .login | Out-Null
    } catch {
        throw 'GitHub CLI no está autenticado. Ejecuta: gh auth login'
    }

    gh secret set ONDA_SPOTIFY_CLIENT_ID --body $spotifyClientId | Out-Null
    gh secret set ONDA_SPOTIFY_REDIRECT_URI --body $spotifyRedirectUri | Out-Null
    Write-Host 'OK  ONDA_SPOTIFY_CLIENT_ID' -ForegroundColor Green
    Write-Host 'OK  ONDA_SPOTIFY_REDIRECT_URI' -ForegroundColor Green
}

# ---------- Build opcional ----------
if ($Build) {
    Push-Location $root
    try {
        .\gradlew assembleRelease --no-daemon
        $apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
        & "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" verify --print-certs $apk |
            Select-String -Pattern 'SHA-256 digest'
    } finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host 'Listo. Crea el tag v1.1.7 para publicar el APK con Spotify activado.' -ForegroundColor Cyan
