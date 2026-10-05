#!/usr/bin/env node
/**
 * build-content : source unique de contenu pour le site et l'application.
 *
 * Entrées
 *   content/<langue>/tour/<chapitre>/<lesson>.md
 *
 * Sorties
 *   site/docs/tour/<chapitre>/<lesson>.mdx     pages du parcours guidé
 *   apps/android/app/src/main/assets/content/content.json
 *   apps/android/app/src/main/assets/content/search-index.json
 *   site/static/examples/index.json            index des exemples du site
 *
 * Usage
 *   node build-content.mjs           génère les sorties
 *   node build-content.mjs --check   valide le contenu et sort en erreur si
 *                                    une leçon est invalide (utilisé en CI)
 */

import {readdir, readFile, writeFile, mkdir, rm} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import YAML from 'yaml';

import {CONTENT_LANGS, validate} from './schema.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..');
const CONTENT_DIR = path.join(ROOT, 'content');
const SITE_DOCS = path.join(ROOT, 'site', 'docs', 'tour');
const ANDROID_ASSETS = path.join(
  ROOT,
  'apps',
  'android',
  'app',
  'src',
  'main',
  'assets',
  'content',
);

const checkOnly = process.argv.includes('--check');

/** Libelles et ordre des chapitres du parcours, utilises par le site. */
const CHAPTER_LABELS = {
  'premiers-pas': 'Premiers pas',
  controle: 'Contrôle',
  donnees: 'Données',
  objets: 'Objets',
  surete: 'Sûreté',
  fonctionnel: 'Fonctionnel',
  structurer: 'Structurer',
  'monde-reel': 'Monde réel',
  qualite: 'Qualité',
  projet: 'Projet',
};

const CHAPTER_POSITIONS = {
  'premiers-pas': 1,
  controle: 2,
  donnees: 3,
  objets: 4,
  surete: 5,
  fonctionnel: 6,
  structurer: 7,
  'monde-reel': 8,
  qualite: 9,
  projet: 10,
};

const GENERATED_NOTICE = `{/*
  Page generee par tools/build-content depuis content/.
  Ne pas editer a la main : modifier la source puis relancer le build.
*/}`;

async function main() {
  const langs = (await listDirs(CONTENT_DIR)).filter((lang) =>
    CONTENT_LANGS.includes(lang),
  );

  if (langs.length === 0) {
    throw new Error(`aucun contenu trouvé dans ${CONTENT_DIR}`);
  }

  const allErrors = [];
  const lessonsByLang = {};

  for (const lang of langs) {
    const lessons = await loadLessons(path.join(CONTENT_DIR, lang, 'tour'));
    lessonsByLang[lang] = lessons;
    for (const lesson of lessons) {
      for (const error of validate(lesson)) {
        allErrors.push(`${lang}/${lesson.file}: ${error}`);
      }
    }
  }

  if (allErrors.length > 0) {
    console.error('Contenu invalide :');
    for (const error of allErrors) {
      console.error(`  - ${error}`);
    }
    process.exitCode = 1;
    return;
  }

  const counts = Object.entries(lessonsByLang).map(
    ([lang, lessons]) => `${lang}: ${lessons.length} leçons`,
  );
  console.log(`Contenu valide (${counts.join(', ')})`);

  if (checkOnly) {
    return;
  }

  for (const [lang, lessons] of Object.entries(lessonsByLang)) {
    if (lang !== 'fr') {
      // Les autres langues sont generees par le site (i18n) ou plus tard.
      continue;
    }
    await generateSitePages(lessons);
  }

  const fr = lessonsByLang.fr ?? [];
  await writeAndroidAssets(fr);
  await generateCategoryFiles(lessonsByLang);

  console.log(
    `Genere ${fr.length} pages de tour, assets Android et index de recherche.`,
  );
}

async function generateSitePages(lessons) {
  const byDir = new Map();
  for (const lesson of lessons) {
    const dir = path.join(SITE_DOCS, lesson.chapitre);
    if (!byDir.has(dir)) {
      byDir.set(dir, []);
    }
    byDir.get(dir).push(lesson);
  }

  for (const [dir, group] of byDir) {
    await rm(dir, {recursive: true, force: true});
    await mkdir(dir, {recursive: true});
    group.sort((a, b) => a.ordre - b.ordre);

    for (const [index, lesson] of group.entries()) {
      await writeFile(
        path.join(dir, `${lesson.id}.mdx`),
        renderLessonPage(lesson, index, group.length),
        'utf8',
      );
    }
  }

  // Nettoie les pages de tour orphelines (lecon supprimee de content/).
  if (existsSync(SITE_DOCS)) {
    const expected = new Set(
      lessons.map((lesson) =>
        path.join(SITE_DOCS, lesson.chapitre, `${lesson.id}.mdx`),
      ),
    );
    for (const chapter of await listDirs(SITE_DOCS)) {
      const chapterDir = path.join(SITE_DOCS, chapter);
      for (const file of await listDirs(chapterDir)) {
        if (!file.endsWith('.mdx')) {
          continue;
        }
        const full = path.join(chapterDir, file);
        if (!expected.has(full)) {
          await rm(full, {force: true});
          console.log(`  supprime ${path.relative(ROOT, full)}`);
        }
      }
    }
  }
}

async function generateCategoryFiles(lessonsByLang) {
  const chapters = new Set(
    (lessonsByLang.fr ?? []).map((lesson) => lesson.chapitre),
  );

  for (const chapter of chapters) {
    const dir = path.join(SITE_DOCS, chapter);
    if (!existsSync(dir)) {
      continue;
    }
    const target = path.join(dir, '_category_.json');
    if (existsSync(target)) {
      continue;
    }
    const category = {
      label: CHAPTER_LABELS[chapter] ?? chapter,
      position: CHAPTER_POSITIONS[chapter] ?? 99,
    };
    await writeFile(target, `${JSON.stringify(category, null, 2)}\n`, 'utf8');
  }
}

async function writeAndroidAssets(lessons) {
  await mkdir(ANDROID_ASSETS, {recursive: true});

  const content = {
    version: 1,
    langue: 'fr',
    chapitres: groupBy(lessons, (lesson) => lesson.chapitre).map(
      ([id, items]) => ({
        id,
        titre: id,
        ordre: Math.min(...items.map((lesson) => lesson.ordre)),
        lessons: items.map(toLessonPayload).sort((a, b) => a.ordre - b.ordre),
      }),
    ),
  };

  await writeFile(
    path.join(ANDROID_ASSETS, 'content.json'),
    `${JSON.stringify(content, null, 2)}\n`,
    'utf8',
  );

  const index = lessons.map((lesson) => ({
    id: lesson.id,
    titre: lesson.titre,
    chapitre: lesson.chapitre,
    resume: lesson.resume ?? '',
    texte: lesson.plain,
  }));

  await writeFile(
    path.join(ANDROID_ASSETS, 'search-index.json'),
    `${JSON.stringify({version: 1, documents: index}, null, 2)}\n`,
    'utf8',
  );
}

function renderLessonPage(lesson, index, total) {
  const frontmatter = [
    '---',
    `id: ${lesson.id}`,
    `title: ${yamlScalar(lesson.titre)}`,
    `sidebar_label: ${yamlScalar(lesson.titre)}`,
    `sidebar_position: ${index + 1}`,
    `description: ${yamlScalar(lesson.resume ?? lesson.titre)}`,
    '---',
    '',
    GENERATED_NOTICE,
    `# ${lesson.titre}`,
    '',
  ];

  const body = [lesson.body.trimEnd(), ''];

  if (lesson.exercice) {
    body.push(
      '## Exercice',
      '',
      lesson.exercice.consigne.trimEnd(),
      '',
      '```dystos',
      (lesson.exercice.code_initial ?? '').trimEnd(),
      '```',
      '',
    );

    if (lesson.exercice.sortie_attendue) {
      body.push(
        'Sortie attendue :',
        '',
        '```',
        lesson.exercice.sortie_attendue.trimEnd(),
        '```',
        '',
      );
    }

    if (lesson.exercice.indices?.length) {
      body.push('<details className="dystos-indices">', '<summary>Indices</summary>', '');
      for (const indice of lesson.exercice.indices) {
        body.push(`- ${indice}`);
      }
      body.push('', '</details>', '');
    }

    if (lesson.exercice.solution) {
      body.push(
        '<details className="dystos-solution">',
        '<summary>Solution</summary>',
        '',
        '```dystos',
        lesson.exercice.solution.trimEnd(),
        '```',
        '',
        '</details>',
        '',
      );
    }
  }

  body.push(
    '---',
    '',
    `_${index + 1} / ${total} — [Parcours guidé](/docs/tour)_`,
    '',
  );

  return [...frontmatter, ...body].join('\n');
}

function toLessonPayload(lesson) {
  return {
    id: lesson.id,
    titre: lesson.titre,
    ordre: lesson.ordre,
    resume: lesson.resume ?? '',
    exemple: lesson.exemple ?? null,
    corps: lesson.body.trim(),
    exercice: lesson.exercice
      ? {
          consigne: lesson.exercice.consigne.trim(),
          codeInitial: lesson.exercice.code_initial ?? '',
          sortieAttendue: lesson.exercice.sortie_attendue ?? null,
          indices: lesson.exercice.indices ?? [],
          solution: lesson.exercice.solution ?? '',
        }
      : null,
  };
}

async function loadLessons(dir) {
  if (!existsSync(dir)) {
    return [];
  }

  const lessons = [];
  const chapters = (await listDirs(dir)).filter((name) =>
    existsSync(path.join(dir, name)),
  );

  for (const chapter of chapters) {
    const chapterDir = path.join(dir, chapter);
    const files = (await readdir(chapterDir)).filter(
      (file) => file.endsWith('.md') || file.endsWith('.mdx'),
    );
    files.sort();

    for (const file of files) {
      const raw = await readFile(path.join(chapterDir, file), 'utf8');
      const lesson = parseLesson(raw, `${chapter}/${file}`);
      lesson.chapitre = lesson.chapitre ?? chapter;
      lessons.push(lesson);
    }
  }

  lessons.sort((a, b) => a.chapitre.localeCompare(b.chapitre) || a.ordre - b.ordre);
  return lessons;
}

function parseLesson(raw, file) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$/.exec(raw);
  if (!match) {
    return {file, body: raw, frontmatter: {}};
  }

  const frontmatter = YAML.parse(match[1]) ?? {};
  const body = match[2];

  return {
    ...frontmatter,
    file,
    body: extractExercise(body, frontmatter),
    plain: stripMarkdown(body),
  };
}

/**
 * Le bloc `exercice:` peut être écrit dans l'en-tête (schéma principal) ou
 * après le corps de la leçon. Dans les deux cas il est retiré du corps.
 */
function extractExercise(body, frontmatter) {
  if (frontmatter.exercice) {
    return body;
  }
  return body.replace(/\n---\n[\s\S]*$/, (match) => match.includes('exercice:') ? '' : match);
}

function stripMarkdown(markdown) {
  return markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_>#-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function yamlScalar(value) {
  const text = String(value ?? '');
  return /[:#\-{}[\]&*?|>'"%@`]/.test(text) || text.trim() !== text
    ? `'${text.replace(/'/g, "''")}'`
    : text;
}

function groupBy(items, keyFn) {
  const map = new Map();
  for (const item of items) {
    const key = keyFn(item);
    if (!map.has(key)) {
      map.set(key, []);
    }
    map.get(key).push(item);
  }
  return [...map.entries()];
}

async function listDirs(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const entries = await readdir(dir, {withFileTypes: true});
  return entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});