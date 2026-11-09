<#
.SYNOPSIS
  Compile tous les modules Dystos sans Maven (equivalent de scripts/build.sh sous Windows).
.DESCRIPTION
  Produit :
    build/dystos.jar            : CLI (lexer, parser, sema, interp, runtime, backend JVM)
    build/dystos-playground.jar : service HTTP du playground (contient tout)
  Necessite un JDK 21 ou plus (JAVA_HOME ou variable PATH, ou installation Eclipse Adoptium).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'find-jdk.ps1')

$jdk = Find-DystosJdk
$javac = Join-Path $jdk 'bin\javac.exe'
$jarTool = Join-Path $jdk 'bin\jar.exe'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $out = Join-Path $root 'build\classes'
    if (Test-Path (Join-Path $root 'build')) { Remove-Item (Join-Path $root 'build') -Recurse -Force }
    New-Item -ItemType Directory -Force $out | Out-Null

    function Compile([string]$module, [string]$release, [bool]$withClasspath) {
        Write-Host "== compilation : $module"
        $files = @(Get-ChildItem $module -Recurse -Filter '*.java' | ForEach-Object FullName)
        if ($files.Count -eq 0) { throw "aucun fichier .java dans $module" }
        $javacArgs = @('--release', $release, '-encoding', 'UTF-8', '-d', $out)
        if ($withClasspath) { $javacArgs += @('-cp', $out) }
        & $javac @javacArgs @files
        if ($LASTEXITCODE -ne 0) { throw "echec de compilation : $module" }
    }

    foreach ($m in 'lexer', 'parser', 'sema', 'interp') { Compile "compiler\$m\src\main" '21' $true }

    Write-Host "== compilation : runtime (Java 17 : les .jar generes l'embarquent)"
    Compile 'compiler\runtime\src\main' '17' $false

    Compile 'compiler\jvm\src\main' '21' $true
    & $jarTool --create --file 'build\dystos.jar' --main-class com.vulsoft.dystos.interp.Main -C $out .
    if ($LASTEXITCODE -ne 0) { throw 'echec de creation de build\dystos.jar' }

    Compile 'services\playground\src\main' '21' $true
    & $jarTool --create --file 'build\dystos-playground.jar' --main-class com.vulsoft.dystos.playground.Playground -C $out .
    if ($LASTEXITCODE -ne 0) { throw 'echec de creation de build\dystos-playground.jar' }

    Write-Host 'OK : build\dystos.jar  build\dystos-playground.jar'
} finally {
    Pop-Location
}
