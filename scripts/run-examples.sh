#!/usr/bin/env bash
# Exécute les exemples de la Phase 0 et affiche le code de sortie de chacun.
# Attendus : 0 partout, sauf l'exemple 11 (2 : null refusé).
# L'exemple 18 (deux modules) est couvert par RunnerTest et ignoré ici.
cd "$(dirname "$0")/.."
DIR=compiler/interp/src/test/resources/exemples
fail=0
for f in "$DIR"/*.dys; do
  n=$(basename "$f")
  case "$n" in 18-*) echo "-- $n : ignoré (multi-modules, voir RunnerTest)"; continue;; esac
  echo "=== $n"
  scripts/dystos run "$f" --sandbox
  code=$?
  expected=0; case "$n" in 11-*) expected=2;; esac
  # l'exemple 15 écrit un fichier : le mode sandbox le refuse (code 1), c'est normal
  case "$n" in 15-*) expected=1;; esac
  if [ "$code" -ne "$expected" ]; then echo "!! code $code (attendu $expected)"; fail=1; fi
done
[ $fail -eq 0 ] && echo "OK : tous les codes de sortie sont conformes" || { echo "ÉCHEC"; exit 1; }
