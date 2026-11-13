import type {ReactNode} from 'react';

import styles from './partenaires.module.css';

/**
 * Surfaces sur lesquelles Dystos s'exécute ou s'intègre.
 *
 * Le bandeau affiche des libellés plutôt que des logos : le projet n'a pas
 * encore d'assets partenaires, et répéter le logo Dystos donnait l'impression
 * d'une image cassée. Les libellés restent sobres et lisibles en monochrome.
 */
const ECOSYSTEME = [
  'Dystos Core',
  'JVM',
  'Android',
  'VS Code',
  'Playground',
  'Documentation',
];

export default function Partenaires(): ReactNode {
  return (
    <section className={styles.partenaires} aria-label="Écosystème Dystos">
      <div className={styles.container}>
        <p className={styles.label}>Écosystème</p>
        <ul className={styles.bandeau}>
          {ECOSYSTEME.map((nom) => (
            <li key={nom}>{nom}</li>
          ))}
        </ul>
      </div>
    </section>
  );
}
