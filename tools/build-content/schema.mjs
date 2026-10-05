/**
 * Schéma du format de contenu Dystos.
 *
 * Une leçon est un fichier Markdown (`.md` ou `.mdx`) avec un en-tête YAML
 * suivi du corps de la leçon. Le corps peut contenir un bloc `exercice:` qui
 * décrit l'exercice associé.
 *
 * Ce fichier est la spécification exécutable : `validate()` est utilisé par le
 * build pour refuser un contenu invalide.
 */

export const CONTENT_LANGS = ['fr', 'en'];

export const CONTENT_CHAPTERS = [
  'premiers-pas',
  'controle',
  'donnees',
  'objets',
  'surete',
  'fonctionnel',
  'structurer',
  'monde-reel',
  'qualite',
  'projet',
];

/** Un exercice associé à une leçon. */
export const exerciseSchema = {
  required: ['consigne'],
  optional: [
    'code_initial',
    'sortie_attendue',
    'indices',
    'solution',
    'tests',
  ],
};

/** Une leçon. */
export const lessonSchema = {
  required: ['id', 'titre', 'chapitre', 'ordre'],
  optional: ['resume', 'exemple', 'exercice', 'duree', 'prerequis'],
};

const ID_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/**
 * Valide une leçon. Retourne la liste des erreurs, vide si tout est correct.
 */
export function validate(lesson) {
  const errors = [];

  for (const key of lessonSchema.required) {
    if (lesson[key] === undefined || lesson[key] === null || lesson[key] === '') {
      errors.push(`champ requis manquant : ${key}`);
    }
  }

  if (lesson.id !== undefined && !ID_PATTERN.test(String(lesson.id))) {
    errors.push(`id invalide : ${lesson.id} (attendu kebab-case ASCII)`);
  }

  if (
    lesson.chapitre !== undefined &&
    !CONTENT_CHAPTERS.includes(String(lesson.chapitre))
  ) {
    errors.push(`chapitre inconnu : ${lesson.chapitre}`);
  }

  if (
    lesson.ordre !== undefined &&
    !Number.isInteger(Number(lesson.ordre))
  ) {
    errors.push(`ordre doit être un entier : ${lesson.ordre}`);
  }

  if (lesson.exercice !== undefined) {
    const exercise = lesson.exercice;
    for (const key of exerciseSchema.required) {
      if (exercise[key] === undefined || exercise[key] === '') {
        errors.push(`exercice : champ requis manquant : ${key}`);
      }
    }
    if (exercise.indices !== undefined && !Array.isArray(exercise.indices)) {
      errors.push('exercice : indices doit être une liste');
    }
    if (
      exercise.sortie_attendue !== undefined &&
      typeof exercise.sortie_attendue !== 'string'
    ) {
      errors.push('exercice : sortie_attendue doit être une chaîne');
    }
  }

  if (!lesson.body || lesson.body.trim() === '') {
    errors.push('corps de la leçon vide');
  }

  return errors;
}