#!/usr/bin/env bash
# Régénère les grammaires de coloration (TextMate, Prism, extension VS Code) dans editors/.
# À relancer après tout changement de mots-clés (Highlighter.java).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -d build/classes ] || scripts/build.sh
java -Dfile.encoding=UTF-8 -cp build/classes tools/GenerateGrammar.java editors
echo "Pensez à lancer : scripts/test-editors.sh"
