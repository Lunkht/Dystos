<#
.SYNOPSIS
  Localise un JDK 21+ utilisable par les scripts PowerShell de Dystos.
.DESCRIPTION
  Ordre de recherche : JAVA_HOME (si valide), installations Eclipse Adoptium, installations Oracle,
  puis javac/java du PATH. Retourne le chemin du dossier du JDK, ou leve une erreur.
#>
function Find-DystosJdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft') {
        if (Test-Path $base) {
            $candidates += (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^jdk' } |
                Sort-Object Name -Descending | ForEach-Object FullName)
        }
    }
    foreach ($c in $candidates) {
        if ((Test-Path (Join-Path $c 'bin\javac.exe')) -and (Test-Path (Join-Path $c 'bin\java.exe'))) { return $c }
    }
    foreach ($tool in 'javac.exe', 'java.exe') {
        $cmd = Get-Command $tool -ErrorAction SilentlyContinue
        if ($cmd) {
            $home = Split-Path (Split-Path $cmd.Source -Parent) -Parent
            if (Test-Path (Join-Path $home 'bin\javac.exe')) { return $home }
        }
    }
    throw "JDK 21 introuvable : installez un JDK 21 (ou plus) et definissez JAVA_HOME."
}
