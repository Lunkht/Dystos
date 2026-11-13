import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';

import styles from './telechargements.module.css';

type Installation = {
  plateforme: string;
  note: string;
  commande: string;
};

const COMPILATEUR: Installation[] = [
  {
    plateforme: 'Linux / macOS',
    note: 'Shell',
    commande: 'curl -fsSL https://dystos.dev/install.sh | sh',
  },
  {
    plateforme: 'Windows',
    note: 'PowerShell',
    commande: 'irm https://dystos.dev/install.ps1 | iex',
  },
  {
    plateforme: 'Depuis les sources',
    note: 'Gradle',
    commande: 'cd compiler && ./gradlew build',
  },
];

const OUTILS = [
  {
    nom: 'Application Android (APK)',
    detail:
      'Le parcours complet hors ligne, l’éditeur coloré, la vérification de code et les exercices.',
    lien: '/telechargements',
  },
  {
    nom: 'F-Droid',
    detail: 'Dépôt auto-hébergé, mise à jour depuis l’application.',
    lien: '/telechargements',
  },
  {
    nom: 'Playground',
    detail: 'Pas d’installation, exécute dans le navigateur.',
    lien: '/playground',
  },
];

const FAITS = [
  {label: 'Mode', valeur: 'Hors ligne'},
  {label: 'Réseau', valeur: 'Aucune permission'},
  {label: 'Contenu', valeur: 'Embarqué'},
];

export default function DownloadsPage(): ReactNode {
  return (
    <Layout
      title="Téléchargements"
      description="Télécharger le compilateur Dystos et l'application Android.">
      <main>
        <header className={styles.hero}>
          <div className={styles.container}>
            <p className={styles.label}>Dystos</p>
            <h1 className={styles.title}>Téléchargements</h1>
            <p className={styles.lede}>
              Tous les artefacts sont publiés par le projet. Rien n’est
              téléchargé depuis un service tiers, et aucune installation ne
              contacte un service externe.
            </p>
          </div>
        </header>

        <section className={styles.section}>
          <div className={styles.container}>
            <div className={styles.sectionHead}>
              <p className={styles.kicker}>Installation</p>
              <h2>Compilateur</h2>
            </div>

            <div className={styles.installGrid}>
              {COMPILATEUR.map((item) => (
                <article key={item.plateforme} className={styles.installCard}>
                  <span className={styles.installLabel}>{item.plateforme}</span>
                  <pre className={styles.command}>
                    <code>{item.commande}</code>
                  </pre>
                  <span className={styles.installNote}>{item.note}</span>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.sectionAlt)}>
          <div className={styles.container}>
            <div className={styles.android}>
              <div>
                <p className={styles.kicker}>Mobile</p>
                <h2>Application Android</h2>
                <p className={styles.body}>
                  L’application embarque le contenu et le compilateur : le
                  parcours, la recherche et la vérification de code fonctionnent
                  en mode avion. Aucune permission réseau n’est requise.
                </p>
                <Link
                  className="button button--primary"
                  to="/telechargements">
                  Versions et APK
                </Link>
              </div>

              <ul className={styles.facts}>
                {FAITS.map((fait) => (
                  <li key={fait.label}>
                    <span>{fait.label}</span>
                    <strong>{fait.valeur}</strong>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.container}>
            <div className={styles.sectionHead}>
              <p className={styles.kicker}>Raccourcis</p>
              <h2>Autres accès</h2>
            </div>

            <div className={styles.accessGrid}>
              {OUTILS.map((outil) => (
                <Link
                  key={outil.nom}
                  to={outil.lien}
                  className={styles.accessCard}>
                  <span className={styles.accessName}>{outil.nom}</span>
                  <span className={styles.accessDetail}>{outil.detail}</span>
                  <span className={styles.accessArrow} aria-hidden="true">
                    →
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.verifySection)}>
          <div className={styles.container}>
            <div className={styles.verify}>
              <div>
                <p className={styles.kicker}>Intégrité</p>
                <h2>Vérifier une source</h2>
                <p className={styles.body}>
                  Chaque version publie une empreinte SHA-256. Comparez-la après
                  téléchargement :
                </p>
              </div>
              <pre className={styles.command}>
                <code>sha256sum dystos-1.0.0.tar.gz</code>
              </pre>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
