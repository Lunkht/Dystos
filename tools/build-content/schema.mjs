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

/** Un exemple : un programme complet, hors parcours. */
export const exampleSchema = {
  required: ['id', 'titre', 'chapitre', 'ordre'],
  optional: ['categorie', 'resume', 'sortie_attendue', 'note'],
};

/** Valide le catalogue de quiz. Retourne la liste des erreurs. */
export function validateQuizzes(quizzes) {
  const errors = [];
  const seenIds = new Set();

  for (const quiz of quizzes) {
    const label = quiz.id ?? '(sans id)';

    if (!quiz.id) {
      errors.push('quiz sans identifiant');
      continue;
    }
    if (seenIds.has(quiz.id)) {
      errors.push(`identifiant de quiz dupliqué : ${quiz.id}`);
    }
    seenIds.add(quiz.id);

    if (!quiz.lecon) {
      errors.push(`${label} : champ requis manquant : lecon`);
    }
    if (!Array.isArray(quiz.questions) || quiz.questions.length === 0) {
      errors.push(`${label} : questions doit être une liste non vide`);
      continue;
    }

    for (const [index, question] of quiz.questions.entries()) {
      const at = `${label} question ${index + 1}`;

      if (!question.question) {
        errors.push(`${at} : énoncé manquant`);
      }
      if (!Array.isArray(question.choix) || question.choix.length < 2) {
        errors.push(`${at} : il faut au moins deux choix`);
        continue;
      }
      if (!question.reponse) {
        errors.push(`${at} : reponse manquant`);
        continue;
      }
      // La correction se fait par contenu : si la bonne réponse n'est pas dans
      // les choix, l'application ne pourra jamais valider la question.
      if (!question.choix.includes(question.reponse)) {
        errors.push(
          `${at} : la reponse "${question.reponse}" ne figure pas parmi les choix`,
        );
      }
      if (new Set(question.choix).size !== question.choix.length) {
        errors.push(`${at} : choix dupliqué`);
      }
    }
  }

  return errors;
}

/** Valide un exemple. Retourne la liste des erreurs, vide si tout est correct. */
export function validateExample(example) {
  const errors = [];

  for (const key of exampleSchema.required) {
    if (example[key] === undefined || example[key] === null || example[key] === '') {
      errors.push(`champ requis manquant : ${key}`);
    }
  }

  if (example.id !== undefined && !ID_PATTERN.test(String(example.id))) {
    errors.push(`id invalide : ${example.id} (attendu kebab-case ASCII)`);
  }

  if (
    example.chapitre !== undefined &&
    !CONTENT_CHAPTERS.includes(String(example.chapitre))
  ) {
    errors.push(`chapitre inconnu : ${example.chapitre}`);
  }

  if (!example.code || example.code.trim() === '') {
    errors.push('code de l\'exemple vide');
  }

  // Un exemple doit dire quelque chose de vérifiable : soit une sortie attendue,
  // soit la raison pour laquelle l'application ne peut pas encore l'exécuter.
  // Sans l'un des deux, l'utilisateur ouvre un exemple et ne comprend pas ce
  // qui se passe quand il appuie sur Exécuter.
  if (!example.sortie_attendue && !example.note) {
    errors.push(
      'ni sortie attendue ni note : l\'exemple est-il exécutable par l\'application ?',
    );
  }

  return errors;
}

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