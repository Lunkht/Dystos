#!/usr/bin/env bash
# Vérifie les grammaires avec les vrais moteurs (vscode-textmate + Oniguruma, Prism). Nécessite Node.js 18+.
set -euo pipefail
cd "$(dirname "$0")/../editors"
[ -d node_modules ] || npm install --no-audit --no-fund
npm test
