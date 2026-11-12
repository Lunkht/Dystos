#!/usr/bin/env node
/**
 * Extrait les 20 exemples du site vers content/fr/exemples/.
 *
 * Les exemples vivaient dans un fichier TypeScript du site, ce qui les rendait
 * invisibles de l'application. Ce script est utilisé une fois pour les migrer
 * vers la source unique de contenu ; ensuite content/ fait foi.
 */

import {readFile, writeFile, mkdir, rm} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..');
const SOURCE = path.join(ROOT, 'site', 'src', 'pages', 'examples.tsx');
const TARGET = path.join(ROOT, 'content', 'fr', 'exemples');

/** Chapitre de chaque exemple, repris de la page du site. */
const CHAPTERS = {
  '01-hello-world': 'premiers-pas',
  '02-variables': 'premiers-pas',
  '03-fonctions': 'premiers-pas',
  '04-conditions': 'controle',
  '05-collections': 'donnees',
  '06-comprehensions': 'donnees',
  '07-f-strings': 'donnees',
  '08-classes': 'objets',
  '09-heritage': 'objets',
  '10-generiques': 'objets',
  '11-null-safety': 'surete',
  '12-exceptions': 'surete',
  '13-lambdas': 'fonctionnel',
  '14-enum-match': 'structurer',
  '15-fichiers': 'monde-reel',
  '16-interop-java': 'monde-reel',
  '17-concurrence': 'monde-reel',
  '18-packages': 'structurer',
  '19-tests': 'qualite',
  '20-gestion-stock': 'projet',
};

async function main() {
  const raw = await readFile(SOURCE, 'utf8');
  const start = raw.indexOf('const EXAMPLES');
  const end = raw.indexOf('\nexport default');
  if (start < 0 || end < 0) {
    throw new Error('tableau EXAMPLES introuvable dans examples.tsx');
  }

  const block = raw.slice(start, end);
  // Le premier exemple est écrit avec des guillemets simples, les suivants
  // avec des backticks : les deux écritures sont acceptées.
  const entries = [...block.matchAll(/\{\s*id: '([^']+)',\s*nom: '([^']+)',\s*chapitre: '([^']+)',\s*code: (?:`([\s\S]*?)`|'((?:[^'\\]|\\.)*)'),\s*sortie: (?:'([^']*)'|`([^`]*)`)?,?\s*\}/g)];

  if (entries.length === 0) {
    throw new Error('aucun exemple extrait');
  }

  await mkdir(TARGET, {recursive: true});

  for (const [index, entry] of entries.entries()) {
    const [, id, nom, chapitre, codeLong, codeCourt, sortieSimple, sortieLong] = entry;
    const code = (codeLong ?? codeCourt ?? '').replace(/\\'/g, "'");
    // La sortie d'un exemple est écrite en JS avec des `\n` littéraux : il faut
    // les remettre en vrais retours à la ligne pour le contenu.
    const sortie = (sortieLong ?? sortieSimple ?? null)?.replace(/\\n/g, '\n') ?? null;
    const file = path.join(TARGET, `${id}.md`);

    const lines = [
      '---',
      `id: ${id}`,
      `titre: ${nom}`,
      `chapitre: ${CHAPTERS[id] ?? chapitre}`,
      `ordre: ${index + 1}`,
      `categorie: ${chapitre}`,
      '---',
      '',
      code.trimEnd(),
      '',
    ];

    if (sortie !== null) {
      lines.push('---', '', 'sortie_attendue: |', ...sortie.split('\n').map((line) => `  ${line}`), '');
    }

    await writeFile(file, lines.join('\n'), 'utf8');
    console.log(`  ${path.relative(ROOT, file)}`);
  }

  console.log(`${entries.length} exemples écrits dans ${path.relative(ROOT, TARGET)}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});