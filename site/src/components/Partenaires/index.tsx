import type {ReactNode} from 'react';
import clsx from 'clsx';

import styles from './partenaires.module.css';

type Partenaire = {
  nom: string;
  url: string;
  logo: string;
};

const PARTENAIRES: Partenaire[] = [
  {nom: 'Dystos Core', url: '/', logo: '/img/dystos.svg'},
  {nom: 'JVM', url: '/', logo: '/img/dystos_for_dark_mode.svg'},
  {nom: 'Android', url: '/', logo: '/img/dystos.svg'},
  {nom: 'VS Code', url: '/', logo: '/img/dystos_for_dark_mode.svg'},
  {nom: 'Playground', url: '/playground', logo: '/img/dystos.svg'},
  {nom: 'Documentation', url: '/docs/introduction', logo: '/img/dystos_for_dark_mode.svg'},
];

export default function Partenaires(): ReactNode {
  return (
    <section className={styles.partenaires}>
      <div className={styles.container}>
        <h2 className={styles.titre}>Nos partenaires</h2>
        <div className={styles.defilant}>
          {PARTENAIRES.map((p) => (
            <img
              key={p.nom}
              src={p.logo}
              alt={p.nom}
              className={styles.logo}
              loading="lazy"
            />
          ))}
          {PARTENAIRES.map((p) => (
            <img
              key={p.nom + '-2'}
              src={p.logo}
              alt={p.nom}
              className={styles.logo}
              loading="lazy"
            />
          ))}
        </div>
      </div>
    </section>
  );
}
