<#
.SYNOPSIS
  Lanceur Dystos pour Windows (equivalent de scripts/dystos).
.DESCRIPTION
  Aucun parametre nomme n'est declare : les arguments sont transmis tels quels a la CLI,
  ce qui permet d'utiliser -o sans ambiguite PowerShell.
.EXAMPLE
  scripts\dystos.ps1 run   compiler\interp\src\test\resources\exemples\01-hello-world.dys
.EXAMPLE
  scripts\dystos.ps1 test  compiler\interp\src\test\resources\exemples\19-tests-integres.dys
.EXAMPLE
  scripts\dystos.ps1 build compiler\interp\src\test\resources\exemples\08-classes-et-constructeurs.dys -o compte.jar
.EXAMPLE
  scripts\dystos.ps1 jrun  compiler\interp\src\test\resources\exemples\01-hello-world.dys
#>

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'build\dystos.jar'
$DystosArgs = @($args)

if (-not (Test-Path $jar)) {
    Write-Host 'build\dystos.jar absent : compilation...' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'build.ps1')
    if (-not (Test-Path $jar)) { throw 'compilation impossible' }
}

. (Join-Path $PSScriptRoot 'find-jdk.ps1')
$jdk = Find-DystosJdk
$javaExe = Join-Path $jdk 'bin\java.exe'

$common = @('-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8')
$command = if ($DystosArgs.Count -gt 0) { $DystosArgs[0] } else { '' }
$rest = if ($DystosArgs.Count -gt 1) { $DystosArgs[1..($DystosArgs.Count - 1)] } else { @() }

switch ($command) {
    'build' { & $javaExe @common -cp $jar com.vulsoft.dystos.jvm.BuildMain build @rest }
    'jrun'  { & $javaExe @common -cp $jar com.vulsoft.dystos.jvm.BuildMain run @rest }
    default { & $javaExe @common -jar $jar @DystosArgs }
}
exit $LASTEXITCODE
