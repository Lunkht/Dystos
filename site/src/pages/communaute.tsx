import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import {Icon} from '@site/src/components/Icon';

import styles from './communaute.module.css';

const REPO_URL = 'https://github.com/Lunkht/Dystos';

type IconName = Parameters<typeof Icon>[0]['name'];

type Etape = {
  titre: string;
  description: string;
  lien: string;
  action: string;
  icone: IconName;
};

const ETAPES: Etape[] = [
  {
    titre: 'Découvrir le langage',
    description:
      'Le parcours guidé part du premier hello world et va jusqu’aux classes, aux collections et aux erreurs.',
    lien: '/docs/tour',
    action: 'Commencer le parcours',
    icone: 'rocket',
  },
  {
    titre: 'Contribuer au projet',
    description:
      'Cloner, compiler, tester, puis proposer une modification. Le guide décrit l’environnement et les conventions.',
    lien: '/docs/communaute/contribuer',
    action: 'Lire le guide',
    icone: 'code',
  },
  {
    titre: 'Signaler un bug',
    description:
      'Un exemple minimal, la sortie attendue et la sortie obtenue suffisent à reproduire la plupart des problèmes.',
    lien: `${REPO_URL}/issues`,
    action: 'Ouvrir une issue',
    icone: 'alert',
  },
  {
    titre: 'Proposer une amélioration',
    description:
      'Une idée de leçon, un exemple manquant, un message d’erreur plus clair : tout se discute dans le dépôt.',
    lien: `${REPO_URL}/issues`,
    action: 'Écrire une proposition',
    icone: 'info',
  },
];

const PRINCIPES = [
  {
    titre: 'Souveraineté',
    description:
      'Aucun service tiers, aucun CDN, aucune police externe. Le site et le compilateur tournent sur l’infrastructure du projet.',
    icone: 'home' as IconName,
  },
  {
    titre: 'Clarté',
    description:
      'Les messages d’erreur sont en français, avec la ligne et la colonne. Une erreur doit expliquer quoi corriger.',
    icone: 'check' as IconName,
  },
  {
    titre: 'Hors ligne',
    description:
      'Le parcours et le compilateur tiennent dans l’application Android. Le mode avion ne coupe rien.',
    icone: 'download' as IconName,
  },
  {
    titre: 'Ouverture',
    description:
      'Le code est publié sous licence MIT. Les décisions de conception sont documentées dans le dépôt.',
    icone: 'github' as IconName,
  },
];

const CONTRIBUTIONS = [
  'Relire une leçon et signaler une phrase floue ou un exemple faux.',
  'Tester l’application Android en mode avion et décrire ce qui casse.',
  'Proposer un exemple court qui illustre une erreur fréquente.',
  'Corriger un message de diagnostic, un libellé ou une faute de français.',
];

const CHARTE = [
  {
    titre: 'On critique le code, pas la personne.',
    description:
      'Les revues portent sur le comportement, la lisibilité et les tests. Les désaccords se règlent avec des exemples.',
  },
  {
    titre: 'Un bug se reproduit.',
    description:
      'Exemple minimal, sortie attendue, sortie obtenue. Pour une erreur de compilation, joindre la sortie JSON de dystos check.',
  },
  {
    titre: 'La souveraineté n’est pas négociable.',
    description:
      'Toute dépendance externe doit être justifiée et validée avant d’entrer dans le dépôt ou dans le site.',
  },
  {
    titre: 'Tout reste sous MIT.',
    description:
      'Une contribution est publiée sous la même licence que le projet. Personne ne s’approprie le travail commun.',
  },
];

function ActionLink({
  href,
  className,
  children,
}: {
  href: string;
  className?: string;
  children: ReactNode;
}): ReactNode {
  if (href.startsWith('http')) {
    return (
      <a
        className={className}
        href={href}
        target="_blank"
        rel="noopener noreferrer">
        {children}
      </a>
    );
  }
  return (
    <Link className={className} to={href}>
      {children}
    </Link>
  );
}

export default function CommunautePage(): ReactNode {
  return (
    <Layout
      title="Communauté"
      description="La communauté Dystos : apprendre, contribuer, signaler un bug et faire vivre un langage libre, souverain et hors ligne.">
      <header className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />

        <div className={clsx('container', styles.heroContent)}>
          <div className={styles.badge}>
            <span className={styles.badgeDot} aria-hidden="true" />
            Communauté Dystos
          </div>
          <h1 className={styles.title}>Un langage se construit à plusieurs</h1>
          <p className={styles.lede}>
            Dystos est libre, souverain et documenté en français. Apprendre,
            signaler, corriger, proposer : chaque contribution passe par des
            outils que le projet contrôle, sans service tiers.
          </p>
          <div className={styles.actions}>
            <ActionLink
              href={REPO_URL}
              className="button button--primary button--lg">
              Rejoindre sur GitHub
            </ActionLink>
            <Link
              className="button button--secondary button--lg"
              to="/docs/communaute/contribuer">
              Guide de contribution
            </Link>
          </div>
        </div>
      </header>

      <main>
        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Par où commencer</h2>
              <p className={styles.sectionText}>
                Quatre portes d’entrée, selon le temps dont tu disposes et ce que
                tu veux apporter.
              </p>
            </div>
            <div className={styles.grid}>
              {ETAPES.map((etape) => (
                <article key={etape.titre} className={styles.card}>
                  <Icon name={etape.icone} className={styles.cardIcon} />
                  <h3 className={styles.cardTitle}>{etape.titre}</h3>
                  <p className={styles.cardText}>{etape.description}</p>
                  <ActionLink href={etape.lien} className={styles.cardLink}>
                    {etape.action}
                  </ActionLink>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.sectionAlt)}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Ce qui nous rassemble</h2>
              <p className={styles.sectionText}>
                Quatre principes qui décident de ce qui entre dans le projet, et
                de ce qui en reste dehors.
              </p>
            </div>
            <div className={styles.grid}>
              {PRINCIPES.map((principe) => (
                <article
                  key={principe.titre}
                  className={clsx(styles.card, styles.principle)}>
                  <Icon name={principe.icone} className={styles.cardIcon} />
                  <h3 className={styles.cardTitle}>{principe.titre}</h3>
                  <p className={styles.cardText}>{principe.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>
                Contribuer sans écrire de code
              </h2>
              <p className={styles.sectionText}>
                Le projet a autant besoin de relectures et de retours que de
                lignes de compilateur.
              </p>
            </div>
            <ul className={styles.list}>
              {CONTRIBUTIONS.map((contribution) => (
                <li key={contribution} className={styles.listItem}>
                  <Icon name="check" size={18} className={styles.check} />
                  <span>{contribution}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className={clsx(styles.section, styles.sectionAlt)}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>La charte de la communauté</h2>
              <p className={styles.sectionText}>
                Peu de règles, mais elles s’appliquent à tout le monde, y compris
                au créateur du langage.
              </p>
            </div>
            <div className={styles.charte}>
              {CHARTE.map((regle, index) => (
                <article key={regle.titre} className={styles.charteCard}>
                  <span className={styles.charteNumber} aria-hidden="true">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <h3 className={styles.cardTitle}>{regle.titre}</h3>
                  <p className={styles.cardText}>{regle.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.ctaSection}>
          <div className="container">
            <div className={styles.cta}>
              <div className={styles.ctaText}>
                <p className={styles.ctaKicker}>Derrière le projet</p>
                <h2 className={styles.ctaTitle}>Une personne, un langage</h2>
                <p className={styles.ctaDescription}>
                  Dystos est conçu et maintenu par Hakim Lunkht. Découvre la
                  démarche, les choix de conception et les liens du projet.
                </p>
              </div>
              <Link
                className="button button--secondary button--lg"
                to="/createur">
                Rencontrer le créateur
              </Link>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
