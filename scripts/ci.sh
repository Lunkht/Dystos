#!/usr/bin/env bash
# Intégration continue locale : tests Maven, exemples, et (si Node est installé) grammaires.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "=== mvn test (racine)"
mvn -q test
echo "=== exemples"
scripts/build.sh >/dev/null
scripts/run-examples.sh
echo "=== interpréteur contre bytecode"
scripts/compare-backends.sh
if command -v node >/dev/null; then
  echo "=== grammaires"
  scripts/test-editors.sh
else
  echo "(Node.js absent : tests des grammaires ignorés)"
fi
echo "CI OK"
