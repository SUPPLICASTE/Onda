<#
.SYNOPSIS
Configura las credenciales de Onda (Firebase, Google y Spotify) en local.properties
y en los GitHub Secrets del repositorio.

.DESCRIPTION
No muestra los valores por pantalla. Ejecútalo desde cualquier directorio:

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

function Read-Plain([string]$prompt) {
    $value = Read-Host -Prompt $prompt
    return $value.Trim()
}

function Read-Hidden([string]$prompt) {
    $value = Read-Host -Prompt $prompt -MaskInput
    return $value.Trim()
}

Write-Host ''
Write-Host '=== Configuración de credenciales de Onda ===' -ForegroundColor Cyan
Write-Host 'Los valores se guardan en local.properties (ignorado por Git) y, si se indica, en GitHub Secrets.'
Write-Host ''

$firebaseApiKey = Read-Plain 'Firebase API key (AIza...)'
$firebaseProjectId = Read-Plain 'Firebase Project ID'
$firebaseAppId = Read-Plain 'Firebase App ID (1:123...:android:...)'
$googleWebClientId = Read-Plain 'Google Web Client ID (...apps.googleusercontent.com)'
$googleClientSecret = Read-Hidden 'Google Client Secret (se usa para importar YouTube)'
$spotifyClientId = Read-Plain 'Spotify Client ID'
$spotifyRedirectUri = Read-Plain 'Spotify Redirect URI [ondaoauth://spotify/callback]'
if ([string]::IsNullOrWhiteSpace($spotifyRedirectUri)) {
    $spotifyRedirectUri = 'ondaoauth://spotify/callback'
}

$values = [ordered]@{
    'onda.firebase.apiKey'        = $firebaseApiKey
    'onda.firebase.projectId'     = $firebaseProjectId
    'onda.firebase.appId'         = $firebaseAppId
    'onda.google.webClientId'     = $googleWebClientId
    'onda.google.clientSecret'    = $googleClientSecret
    'onda.spotify.clientId'       = $spotifyClientId
    'onda.spotify.redirectUri'    = $spotifyRedirectUri
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

    $secrets = [ordered]@{
        'ONDA_FIREBASE_API_KEY'       = $firebaseApiKey
        'ONDA_FIREBASE_PROJECT_ID'    = $firebaseProjectId
        'ONDA_FIREBASE_APP_ID'        = $firebaseAppId
        'ONDA_GOOGLE_WEB_CLIENT_ID'   = $googleWebClientId
        'ONDA_GOOGLE_CLIENT_SECRET'   = $googleClientSecret
        'ONDA_SPOTIFY_CLIENT_ID'      = $spotifyClientId
        'ONDA_SPOTIFY_REDIRECT_URI'   = $spotifyRedirectUri
    }

    foreach ($name in $secrets.Keys) {
        $value = $secrets[$name]
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        gh secret set $name --body $value | Out-Null
        Write-Host "OK  $name" -ForegroundColor Green
    }
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
Write-Host 'Listo. En GitHub, crea un tag v1.1.6 para publicar el APK con estas credenciales.' -ForegroundColor Cyan
Write-Host 'Recuerda añadir ondaoauth://spotify/callback en el Dashboard de Spotify y habilitar Google en Firebase.'
