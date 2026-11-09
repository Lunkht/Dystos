#!/usr/bin/env bash
# Compare l'interpréteur et le bytecode sur les charges de benchmarks/ (une mesure par programme : ce sont des ordres
# de grandeur, pas des chiffres de laboratoire ; le temps du bytecode inclut le démarrage de la JVM).
cd "$(dirname "$0")/.."
[ -f build/dystos.jar ] || scripts/build.sh >/dev/null
mkdir -p build/bench
now() { date +%s.%N; }
printf "%-10s %14s %10s %8s  %s\n" programme interpréteur bytecode gain sortie
for f in benchmarks/*.dys; do
  n=$(basename "$f" .dys)
  scripts/dystos build "$f" -o "build/bench/$n.jar" >/dev/null || exit 1
  t0=$(now); a=$(scripts/dystos run "$f" 2>&1); t1=$(now)
  b=$(java -jar "build/bench/$n.jar" 2>&1); t2=$(now)
  same="identique"; [ "$a" = "$b" ] || same="DIFFÉRENTE"
  awk -v n="$n" -v a="$t0" -v b="$t1" -v c="$t2" -v s="$same" \
      'BEGIN { ti=b-a; tj=c-b; printf "%-10s %13.2fs %9.2fs %7.1fx  %s\n", n, ti, tj, ti/tj, s }'
done
