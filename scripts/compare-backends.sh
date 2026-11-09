#!/usr/bin/env bash
# Exécute chaque exemple avec l'interpréteur ET avec le bytecode compilé ; compare sortie et code de sortie.
#   IDENTIQUE     : mêmes sortie et code
#   NON SUPPORTÉ  : le backend JVM refuse la construction (E0401) : normal pour l'instant
#   DIFFÉRENT     : divergence = bug dans l'un des deux backends (le script échoue)
cd "$(dirname "$0")/.."
DIR=compiler/interp/src/test/resources/exemples
status=0
for f in "$DIR"/*.dys; do
  n=$(basename "$f" .dys)
  case "$n" in 18-*) continue;; esac
  scripts/dystos run "$f" >/tmp/dys-exp.txt 2>/dev/null; e1=$?
  scripts/dystos jrun "$f" >/tmp/dys-act.txt 2>/tmp/dys-act.err; e2=$?
  if [ $e2 -eq 2 ] && grep -q E0401 /tmp/dys-act.err; then
    printf "NON SUPPORTÉ  %s : %s\n" "$n" "$(grep E0401 /tmp/dys-act.err | head -1 | cut -d']' -f2 | cut -c1-110)"
  elif [ $e1 -eq $e2 ] && cmp -s /tmp/dys-exp.txt /tmp/dys-act.txt; then
    printf "IDENTIQUE     %s\n" "$n"
  else
    printf "DIFFÉRENT     %s (interpréteur=%s, bytecode=%s)\n" "$n" "$e1" "$e2"
    diff /tmp/dys-exp.txt /tmp/dys-act.txt | head -5
    status=1
  fi
done
exit $status
