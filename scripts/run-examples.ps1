<#
.SYNOPSIS
  Execute les exemples de la Phase 0 sous Windows et verifie les codes de sortie.
.DESCRIPTION
  Attendus : 0 partout, sauf l'exemple 11 (2 : null refuse) et l'exemple 15 en mode sandbox (1 : ecriture refusee).
  L'exemple 18 (deux modules) est couvert par RunnerTest et ignore ici.
#>

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$dir = Join-Path $root 'compiler\interp\src\test\resources\exemples'
$dystos = Join-Path $PSScriptRoot 'dystos.ps1'
$fail = 0

Get-ChildItem $dir -Filter '*.dys' | Sort-Object Name | ForEach-Object {
    $name = $_.Name
    if ($name -like '18-*') {
        Write-Host "-- $name : ignore (multi-modules, voir RunnerTest)"
        return
    }
    Write-Host "=== $name"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $dystos run $_.FullName --sandbox
    $code = $LASTEXITCODE
    $expected = 0
    if ($name -like '11-*') { $expected = 2 }
    if ($name -like '15-*') { $expected = 1 }
    if ($code -ne $expected) {
        Write-Host "!! code $code (attendu $expected)" -ForegroundColor Red
        $script:fail++
    }
}

if ($script:fail -eq 0) {
    Write-Host 'OK : tous les codes de sortie sont conformes' -ForegroundColor Green
} else {
    Write-Host 'ECHEC' -ForegroundColor Red
    exit 1
}
