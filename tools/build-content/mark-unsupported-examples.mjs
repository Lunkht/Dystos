/**
 * Passe en revue les exemples dont le moteur Android ne sait pas encore
 * executer la construction illustree : la sortie attendue est retiree et une
 * note explique ce qui manque.
 *
 * C'est un script de migration ponctuel. La regle qu'il applique est desormais
 * verifiee par ExampleCatalogTest, cote application.
 */
import {readFile, writeFile, readdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const DIR = path.join(ROOT, 'content', 'fr', 'exemples');

const NOTES = {
  '05-collections':
    'Nécessite les méthodes de collection (.add, .items) et les ensembles, pas encore implémentés dans l’application.',
  '06-comprehensions':
    'Nécessite les compréhensions de liste, de dictionnaire et d’ensemble, pas encore implémentées dans l’application.',
  '07-f-strings':
    'Nécessite les méthodes de chaîne (.upper, .length, .split, .join) et les tranches, pas encore implémentées dans l’application.',
  '08-classes':
    'Nécessite les classes et la méthode init, pas encore implémentées dans l’application.',
  '09-heritage':
    'Nécessite l’héritage de classes, les interfaces et super, pas encore implémentés dans l’application.',
  '10-generiques':
    'Nécessite les types génériques, pas encore implémentés dans l’application.',
  '11-null-safety':
    'Nécessite les types nullables et les opérateurs ?., ?:, pas encore implémentés dans l’application.',
  '12-exceptions':
    'Nécessite les exceptions et le bloc try, pas encore implémentés dans l’application.',
  '13-lambdas':
    'Nécessite les fonctions lambda et les fonctions d’ordre supérieur, pas encore implémentées dans l’application.',
  '14-enum-match':
    'Nécessite les énumérations et le motif match, pas encore implémentés dans l’application.',
  '15-fichiers':
    'Nécessite la lecture et l’écriture de fichiers, pas encore implémentées dans l’application.',
  '16-interop-java':
    'Nécessite l’interopérabilité Java, volontairement absente de l’application mobile.',
  '17-concurrence':
    'Nécessite la concurrence (threads, verrous), volontairement absente de l’application mobile.',
  '18-packages':
    'Nécessite les packages et la visibilité, volontairement absents de l’application mobile.',
  '19-tests':
    'Nécessite le décorateur @test et la commande dystos test, absents de l’application mobile.',
  '20-gestion-stock':
    'Nécessite les classes, les collections et la null-sûreté, pas encore implémentées dans l’application.',
};

const files = (await readdir(DIR)).filter((name) => name.endsWith('.md'));

for (const name of files) {
  const id = name.replace(/\.md$/, '');
  const note = NOTES[id];
  if (!note) {
    continue;
  }

  const file = path.join(DIR, name);
  const source = await readFile(file, 'utf8');

  const firstFence = source.indexOf('---');
  const frontmatterEnd = source.indexOf('---', firstFence + 3);
  const frontmatter = source.slice(firstFence + 3, frontmatterEnd);
  const body = source.slice(frontmatterEnd + 3).split('\n---')[0];

  const lines = frontmatter
    .split('\n')
    .filter((line) => !line.startsWith('sortie_attendue:'));
  const rebuilt = `---\n${lines.join('\n').trim()}\nnote: >-\n  ${note}\n---\n${body}\n`;

  await writeFile(file, rebuilt, 'utf8');
  console.log(`mis a jour : ${name}`);
}