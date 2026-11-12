#!/usr/bin/env node
/**
 * Vérifie que la grammaire Prism du site reste alignée sur celle des éditeurs.
 *
 * `editors/prism/prism-dystos.js` est généré par tools/GenerateGrammar.java à
 * partir du lexer : c'est la source de vérité. Le site en contient un portage
 * TypeScript (src/dystos/prism-dystos.ts). Ce script compare les expressions
 * régulières des deux fichiers et échoue si l'une a divergé.
 *
 * Usage : node scripts/check-prism.mjs
 */

import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..');

const SOURCE = path.join(ROOT, 'editors', 'prism', 'prism-dystos.js');
const PORT = path.join(ROOT, 'site', 'src', 'dystos', 'prism-dystos.ts');

/** Toutes les expressions régulières littérales d'un fichier, commentaires exclus. */
async function regexesOf(file) {
  const text = await readFile(file, 'utf8');
  const found = new Set();
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (
      trimmed.startsWith('//') ||
      trimmed.startsWith('/*') ||
      trimmed.startsWith('*')
    ) {
      continue;
    }
    // Un littéral /.../flags : le premier / ouvre, le dernier ferme.
    for (const match of line.matchAll(/\/(?:[^/\\\n]|\\.)+\/[a-z]*/g)) {
      found.add(match[0]);
    }
  }
  return found;
}

function missing(reference, candidate) {
  return [...reference].filter((regex) => !candidate.has(regex));
}

const source = await regexesOf(SOURCE);
const port = await regexesOf(PORT);

const absentFromPort = missing(source, port);
const extraInPort = missing(port, source);

if (absentFromPort.length === 0 && extraInPort.length === 0) {
  console.log(
    `Prism : ${source.size} expressions identiques entre editors/ et site/.`,
  );
  process.exit(0);
}

console.error('Prism : la grammaire du site a divergé de editors/prism.');
for (const regex of absentFromPort) {
  console.error(`  manquante dans site/ : ${regex}`);
}
for (const regex of extraInPort) {
  console.error(`  en trop dans site/   : ${regex}`);
}
console.error(
  'Corrigez site/src/dystos/prism-dystos.ts (ou régénérez editors/prism/prism-dystos.js).',
);
process.exit(1);
