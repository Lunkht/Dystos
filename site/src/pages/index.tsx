import {useEffect, useState, type ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Layout from '@theme/Layout';
import {Icon} from '@site/src/components/Icon';

import LogoDystos from '@site/static/img/dystos.svg';
import LogoDystosDark from '@site/static/img/dystos_for_dark_mode.svg';
import Partenaires from '@site/src/components/Partenaires';

import styles from './index.module.css';

const HERO_CODE = `enum Role:
    ADMIN
    CLIENT

class Compte:
    _solde: float = 0.0

    def init(self, nom: str, role: Role):
        self.nom = nom
        self.role = role

    def deposer(self, montant: float) -> float:
        if montant <= 0:
            raise ValueError("montant invalide")
        self._solde += montant
        return self._solde

    def toString(self) -> str:
        return f"{self.nom} : {self._solde:.2f}"

def main():
    for compte in [Compte("Awa", Role.CLIENT), Compte("Moussa", Role.ADMIN)]:
        print(compte.deposer(150.0))`;

type PointFort = {
  title: string;
  description: string;
  iconName: Parameters<typeof Icon>[0]['name'];
};

const POINTS_FORTS: PointFort[] = [
  {
    title: 'Lisible comme Python',
    description:
      'Indentation significative, pas de point-virgule, mots-clés explicites. On lit du code Dystos sans le connaître.',
    iconName: 'code',
  },
  {
    title: 'Sûr comme Java',
    description:
      'Types statiques, null-safety avec ? et ?., exceptions explicites. Les erreurs apparaissent avant l’exécution.',
    iconName: 'coffee',
  },
  {
    title: 'Tourne sur la JVM',
    description:
      'Interopérabilité directe avec Java et la bibliothèque standard JVM. Tu réutilises l’écosystème existant.',
    iconName: 'rocket',
  },
  {
    title: 'Autohébergé',
    description:
      'Aucun service tiers, aucune police externe, aucun traçage. Le site et le compilateur tournent sur ton infrastructure.',
    iconName: 'home',
  },
];

const EXAMPLES = [
  {
    id: '01-hello-world',
    name: 'Hello world',
    code: 'print("Bonjour, Dystos !")',
    output: 'Bonjour, Dystos !',
  },
  {
    id: '04-conditions',
    name: 'Conditions',
    code: `age: int = 20
if age >= 18:
    print("majeur")
else:
    print("mineur")`,
    output: 'majeur',
  },
  {
    id: '05-collections',
    name: 'Compréhensions',
    code: `nombres = [1, 2, 3, 4]
carres = [n * n for n in nombres]
print(carres)`,
    output: '[1, 4, 9, 16]',
  },
  {
    id: '20-gestion-stock',
    name: 'Gestion de stock',
    code: `produits = {"clavier": 5, "souris": 12}
for nom, qte in produits.items():
    print(nom, qte)`,
    output: 'clavier 5\nsouris 12',
  },
];

export default function Home(): ReactNode {
  const [isDark, setIsDark] = useState<boolean | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    const update = () => {
      setIsDark(root.dataset.theme === 'dark');
    };
    update();

    // Écoute les changements de thème (bascule clair/sombre de Docusaurus)
    const observer = new MutationObserver(() => update());
    observer.observe(root, {attributes: true, attributeFilter: ['data-theme']});

    return () => observer.disconnect();
  }, []);

  const Logo = isDark === false ? LogoDystos : LogoDystosDark;

  return (
    <Layout
      title="Dystos, un langage simple et sûr"
      description="Dystos est un langage de programmation lisible comme Python, sûr comme Java, qui tourne sur la JVM. Apprends-le sur le web ou hors ligne sur Android.">
      {/* ======== HERO ======== */}
      <header className={clsx('hero hero--primary', styles.heroBanner)}>
        {/* Grille de fond statique */}
        <div className={styles.heroGrid} aria-hidden="true" />

        <div className="container">
          <div className={styles.heroContent}>
            <div className={styles.heroBadge}>
              <span className={styles.heroBadgeDot} aria-hidden="true" />
              Dystos — Langage de programmation moderne
            </div>
            <h1 className={styles.heroTitle}>
              <Logo className={styles.heroLogo} />
            </h1>
            <p className={styles.heroTagline}>
              Un langage lisible comme Python, sûr comme Java, qui tourne sur la
              JVM. Écris, teste et compile sans rien installer.
            </p>
            <div className={styles.buttons}>
              <Link className="button button--primary button--lg" to="/playground">
                Essayer en ligne
              </Link>
              <Link className="button button--secondary button--lg" to="/docs/tour">
                Commencer
              </Link>
            </div>
          </div>
          <div className={styles.heroCodeWrapper}>
            <div className={styles.heroCode}>
              <CodeBlock language="dystos">{HERO_CODE}</CodeBlock>
            </div>
          </div>
        </div>
      </header>

      <main>
        {/* ======== SECTION POINTS FORTS ======== */}
        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Pourquoi Dystos ?</h2>
              <p className={styles.sectionSubtitle}>
                Quatre piliers pour apprendre et programmer en toute confiance.
              </p>
            </div>
            <div className={styles.featureGrid}>
              {POINTS_FORTS.map((point, i) => (
                <article
                  key={point.title}
                  className={clsx(styles.feature, `feature-${i + 1}`)}>
                  <Icon name={point.iconName} className={styles.featureIcon} />
                  <h3>{point.title}</h3>
                  <p>{point.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* ======== SECTION EXEMPLES ======== */}
        <section className={clsx(styles.section, styles.examplesSection)}>
          <div className="container">
            <div className={styles.sectionHeader}>
              <h2 className={styles.sectionTitle}>Quelques exemples</h2>
              <p className={styles.sectionSubtitle}>
                Vingt programmes courts, du premier hello à une gestion de stock
                complète.
              </p>
            </div>
            <div className={styles.exampleGrid}>
              {EXAMPLES.map((example) => (
                <article key={example.id} className={styles.example}>
                  <h3 className={styles.exampleName}>{example.name}</h3>
                  <CodeBlock language="dystos">{example.code}</CodeBlock>
                  <pre className={styles.output}>
                    <span className={styles.outputLabel}>sortie</span>
                    {example.output}
                  </pre>
                </article>
              ))}
            </div>
            <div className={styles.centered}>
              <Link className="button button--secondary" to="/examples">
                Voir tous les exemples
              </Link>
            </div>
          </div>
        </section>

        <Partenaires />
      </main>
    </Layout>
  );
}
