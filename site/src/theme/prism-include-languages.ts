import type * as PrismNamespace from 'prismjs';
import {registerDystosLanguage} from '@site/src/dystos/prism-dystos';

/**
 * Enregistre les langages supplémentaires configurés dans docusaurus.config.ts,
 * puis la grammaire Dystos (mots-clés, opérateurs, littéraux).
 *
 * Cette copie remplace src/theme/prism-include-languages.ts du thème classique
 * afin de pouvoir définir un langage qui n'existe pas dans prismjs.
 */
export default function prismIncludeLanguages(
  PrismObject: typeof PrismNamespace,
): void {
  registerDystosLanguage(PrismObject as unknown as {
    languages: Record<string, unknown>;
  });
}