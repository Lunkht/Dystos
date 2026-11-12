import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Layout from '@theme/Layout';
import {Icon} from '@site/src/components/Icon';

import styles from './createur.module.css';

const REPO_URL = 'https://github.com/Lunkht/Dystos';
const PROFIL_URL = 'https://github.com/Lunkht';

const EXEMPLE = `class Compte:
    titulaire: str
    _solde: float = 0.0

    def init(self, titulaire: str):
        self.titulaire = titulaire

    def deposer(self, montant: float):
        self._solde += montant

    def solde(self) -> float:
        return self._solde

def main():
    compte = Compte("Awa")
    compte.deposer(150.0)
    print(compte.solde())`;

const REPERES = [
  {valeur: '281', label: 'tests automatisés'},
  {valeur: '20', label: 'programmes d’exemple'},
  {valeur: '2', label: 'moteurs d’exécution'},
  {valeur: '0', label: 'service tiers'},
];

const PRINCIPES = [
  {
    titre: 'La lisibilité d’abord',
    description:
      'Indentation significative, pas de point-virgule, mots-clés explicites. On doit pouvoir lire du Dystos sans l’avoir appris.',
    icone: 'code' as const,
  },
  {
    titre: 'Les erreurs avant l’exécution',
    description:
      'Typage statique, null-safety avec ? et ?., diagnostics positionnés. Le compilateur explique ce qui ne va pas, en français.',
    icone: 'alert' as const,
  },
  {
    titre: 'Rien ne dépend d’un tiers',
    description:
      'Compilateur, site, playground et application vivent dans le dépôt. Aucun CDN, aucune police externe, aucun traçage.',
    icone: 'home' as const,
  },
  {
    titre: 'Une seule source de contenu',
    description:
      'Une leçon est écrite une fois en Markdown, puis générée pour le site et pour l’application Android. Le build échoue si elle est invalide.',
    icone: 'terminal' as const,
  },
];

const LIENS = [
  {
    titre: 'Le dépôt',
    description:
      'Le compilateur, les tests, la documentation, le site et le contenu pédagogique.',
    url: REPO_URL,
    action: 'github.com/Lunkht/Dystos',
    icone: 'github' as const,
  },
  {
    titre: 'Les issues',
    description:
      'Un bug reproductible, une idée de leçon ou un message d’erreur à améliorer.',
    url: `${REPO_URL}/issues`,
    action: 'Ouvrir une issue',
    icone: 'alert' as const,
  },
  {
    titre: 'Le carnet de bord',
    description:
      'Les versions publiées et les décisions de conception, racontées au fil du projet.',
    url: '/blog',
    action: 'Lire les actualités',
    icone: 'rocket' as const,
  },
];

function LienExterne({
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

export default function CreateurPage(): ReactNode {
  return (
    <Layout
      title="Hakim Lunkht, créateur de Dystos"
      description="Hakim Lunkht est le créateur et le mainteneur de Dystos, un langage lisible comme Python, sûr comme Java et souverain sur la JVM.">
      <header className={styles.hero}>
        <div className={styles.heroGrid} aria-hidden="true" />
        <div className={clsx(styles.glow, styles.glowA)} aria-hidden="true" />
        <div className={clsx(styles.glow, styles.glowB)} aria-hidden="true" />

        <div className={clsx('container', styles.heroContent)}>
          <div className={styles.avatar} aria-hidden="true">
            HL
          </div>
          <div className={styles.badge}>
            <span className={styles.badgeDot} aria-hidden="true" />
            Le créateur de Dystos
          </div>
          <h1 className={styles.title}>Hakim Lunkht</h1>
          <p className={styles.role}>Créateur et mainteneur</p>
          <p className={styles.lede}>
            Il conçoit et développe Dystos : le langage, le compilateur, le site
            et l’application Android. Un projet mené en français, sans service
            tiers, avec une exigence simple : que le code reste lisible et que
            les erreurs arrivent avant l’exécution.
          </p>
          <div className={styles.tags}>
            <span className={styles.tag}>Langages</span>
            <span className={styles.tag}>Compilateurs</span>
            <span className={styles.tag}>JVM</span>
            <span className={styles.tag}>Android</span>
            <span className={styles.tag}>Français</span>
          </div>
          <div className={styles.actions}>
            <LienExterne
              href={PROFIL_URL}
              className="button button--primary button--lg">
              Profil GitHub
            </LienExterne>
            <Link className="button button--secondary button--lg" to="/communaute">
              Rejoindre la communauté
            </Link>
          </div>
        </div>
      </header>

      <main>
        <section className={styles.section}>
          <div className="container">
            <div className={styles.story}>
              <div className={styles.prose}>
                <p className={styles.kicker}>Le parcours</p>
                <h2 className={styles.sectionTitle}>
                  Un langage né d’un compromis refusé
                </h2>
                <p>
                  Dystos part d’un constat : on oppose souvent la lisibilité de
                  Python à la sûreté de Java, comme s’il fallait choisir. Hakim
                  Lunkht a décidé de réunir les deux, sans renoncer à
                  l’écosystème de la JVM.
                </p>
                <p>
                  Le langage emprunte à Python ses blocs par indentation, ses
                  f-strings et ses compréhensions. Il emprunte à Java ses types
                  statiques, ses classes, ses interfaces et sa null-safety. Le
                  tout est implémenté en Java, compilé vers la JVM et documenté en
                  français, avec des messages d’erreur qui indiquent la ligne et
                  la colonne.
                </p>
                <p>
                  Autour du compilateur, le projet construit un parcours
                  d’apprentissage, un playground qui exécute dans le navigateur,
                  une extension d’éditeur et une application Android qui embarque
                  le contenu et le moteur. Une seule règle traverse l’ensemble :
                  rien ne doit dépendre d’un service que le projet ne contrôle
                  pas.
                </p>
                <p>
                  Dystos est distribué sous licence MIT. Le dépôt contient le
                  code, les tests, la documentation et les leçons : tout ce qu’il
                  faut pour comprendre une décision, la contester et la corriger.
                </p>
              </div>

              <aside className={styles.aside}>
                <h3 className={styles.asideTitle}>En bref</h3>
                <ul className={styles.asideList}>
                  <li className={styles.asideItem}>
                    <span className={styles.asideLabel}>Rôle</span>
                    <span className={styles.asideValue}>
                      Créateur et mainteneur
                    </span>
                  </li>
                  <li className={styles.asideItem}>
                    <span className={styles.asideLabel}>Projet</span>
                    <span className={styles.asideValue}>Dystos</span>
                  </li>
                  <li className={styles.asideItem}>
                    <span className={styles.asideLabel}>Langue</span>
                    <span className={styles.asideValue}>Français</span>
                  </li>
                  <li className={styles.asideItem}>
                    <span className={styles.asideLabel}>Licence</span>
                    <span className={styles.asideValue}>MIT</span>
                  </li>
                  <li className={styles.asideItem}>
                    <span className={styles.asideLabel}>Contact</span>
                    <span className={styles.asideValue}>
                      Issues GitHub du dépôt
                    </span>
                  </li>
                </ul>
                <LienExterne href={PROFIL_URL} className={styles.asideLink}>
                  Voir le profil GitHub
                </LienExterne>
              </aside>
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.sectionAlt)}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Le projet en repères</h2>
              <p className={styles.sectionText}>
                Des chiffres vérifiés dans le dépôt, pas des promesses de page
                d’accueil.
              </p>
            </div>
            <div className={styles.stats}>
              {REPERES.map((repere) => (
                <div key={repere.label} className={styles.stat}>
                  <span className={styles.statValue}>{repere.valeur}</span>
                  <span className={styles.statLabel}>{repere.label}</span>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>
                Ce qui guide les décisions
              </h2>
              <p className={styles.sectionText}>
                Quatre règles de conception, appliquées au langage comme au site.
              </p>
            </div>
            <div className={styles.cards}>
              {PRINCIPES.map((principe) => (
                <article key={principe.titre} className={styles.card}>
                  <Icon name={principe.icone} className={styles.cardIcon} />
                  <h3 className={styles.cardTitle}>{principe.titre}</h3>
                  <p className={styles.cardText}>{principe.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.sectionAlt)}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Le langage en un exemple</h2>
              <p className={styles.sectionText}>
                Une classe, un constructeur, une méthode typée. Le programme
                complet tient en quelques lignes lisibles.
              </p>
            </div>
            <div className={styles.codeWrapper}>
              <CodeBlock language="dystos">{EXEMPLE}</CodeBlock>
            </div>
            <p className={styles.codeNote}>
              Extrait de l’exemple 08 du dépôt, exécuté par les tests du
              compilateur. Sortie : <code>150.0</code>.
            </p>
          </div>
        </section>

        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Échanger avec le créateur</h2>
              <p className={styles.sectionText}>
                Le dépôt est le lieu unique de discussion : pas de formulaire, pas
                d’outil tiers.
              </p>
            </div>
            <div className={styles.cards}>
              {LIENS.map((lien) => (
                <article key={lien.titre} className={styles.card}>
                  <Icon name={lien.icone} className={styles.cardIcon} />
                  <h3 className={styles.cardTitle}>{lien.titre}</h3>
                  <p className={styles.cardText}>{lien.description}</p>
                  <LienExterne href={lien.url} className={styles.cardLink}>
                    {lien.action}
                  </LienExterne>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={styles.ctaSection}>
          <div className="container">
            <div className={styles.cta}>
              <div className={styles.ctaText}>
                <p className={styles.ctaKicker}>La suite</p>
                <h2 className={styles.ctaTitle}>
                  Un langage ne se maintient pas seul
                </h2>
                <p className={styles.ctaDescription}>
                  Relectures, exemples, rapports de bugs, tests sur Android : la
                  communauté fait partie de la construction de Dystos.
                </p>
              </div>
              <Link
                className="button button--secondary button--lg"
                to="/communaute">
                Découvrir la communauté
              </Link>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
