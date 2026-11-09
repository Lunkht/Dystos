#!/usr/bin/env bash
# Compile tous les modules sans Maven.
#   build/dystos.jar            : CLI (lexer, parser, sema, interp, runtime, backend JVM)
#                                 lanceur par défaut : com.vulsoft.dystos.interp.Main
#   build/dystos-playground.jar : service HTTP du playground (contient tout)
set -euo pipefail
cd "$(dirname "$0")/.."

command -v javac >/dev/null || { echo "javac introuvable : installez un JDK 21 (ou plus)." >&2; exit 1; }

OUT=build/classes
rm -rf build && mkdir -p "$OUT"
for m in lexer parser sema interp; do
  echo "== compilation : $m"
  javac --release 21 -encoding UTF-8 -d "$OUT" -cp "$OUT" $(find "compiler/$m/src/main" -name '*.java')
done
echo "== compilation : runtime (Java 17 : les .jar générés l'embarquent)"
javac --release 17 -encoding UTF-8 -d "$OUT" $(find compiler/runtime/src/main -name '*.java')
echo "== compilation : jvm"
javac --release 21 -encoding UTF-8 -d "$OUT" -cp "$OUT" $(find compiler/jvm/src/main -name '*.java')
jar --create --file build/dystos.jar --main-class com.vulsoft.dystos.interp.Main -C "$OUT" .
echo "== compilation : playground"
javac --release 21 -encoding UTF-8 -d "$OUT" -cp "$OUT" $(find services/playground/src/main -name '*.java')
jar --create --file build/dystos-playground.jar --main-class com.vulsoft.dystos.playground.Playground -C "$OUT" .
echo "OK : build/dystos.jar  build/dystos-playground.jar"
