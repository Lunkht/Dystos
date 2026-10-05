import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Layout from '@theme/Layout';

import styles from './index.module.css';

const HERO_CODE = `enum Role:
    ADMIN
    CLIENT

class Compte:
    _solde: float = 0.0

    def __init__(self, nom: string, role: Role):
        self.nom = nom
        self.role = role

    def deposer(self, montant: float) -> float:
        if montant <= 0:
            throw ValueError("montant invalide")
        self._solde += montant
        return self._solde

    def __str__(self) -> string:
        return f"{self.nom} : {self._solde:.2f}"

def main():
    for compte in [Compte("Awa", Role.CLIENT), Compte("Moussa", Role.ADMIN)]:
        print(compte.deposer(150.0))`;

const POINTS_FORTS = [
  {
    title: 'Lisible comme Python',
    description:
      'Indentation significant, pas de point-virgule, mots-clés explicites. On lit du code Dystos sans le connaître.',
  },
  {
    title: 'Sûr comme Java',
    description:
      'Types statiques, null-safety avec ? et ?., exceptions explicites. Les erreurs apparaissent avant lexécution.',
  },
  {
    title: 'Tourne sur la JVM',
    description:
      'Interopérabilité directe avec Java et la bibliothèque standard JVM. Tu réutilises lécosystème existant.',
  },
  {
    title: 'Autohébergé',
    description:
      'Aucun service tiers, aucune police externe, aucun traçage. Le site et le compilateur tournent sur ton infrastructure.',
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
  return (
    <Layout
      title="Dystos, un langage simple et sûr"
      description="Dystos est un langage de programmation lisible comme Python, sûr comme Java, qui tourne sur la JVM. Apprends-le sur le web ou hors ligne sur Android.">
      <header className={clsx('hero hero--primary', styles.heroBanner)}>
        <div className="container">
          <h1 className={styles.heroTitle}>
            Dystos<span className={styles.heroAccent}>.</span>
          </h1>
          <p className={styles.heroTagline}>
            Un langage lisible comme Python, sûr comme Java, qui tourne sur la
            JVM.
          </p>
          <div className={styles.buttons}>
            <Link className="button button--primary button--lg" to="/playground">
              Essayer en ligne
            </Link>
            <Link className="button button--secondary button--lg" to="/docs/tour">
              Commencer
            </Link>
          </div>
          <div className={styles.heroCode}>
            <CodeBlock language="dystos">{HERO_CODE}</CodeBlock>
          </div>
        </div>
      </header>

      <main>
        <section className={styles.section}>
          <div className="container">
            <div className={styles.featureGrid}>
              {POINTS_FORTS.map((point) => (
                <article key={point.title} className={styles.feature}>
                  <h3>{point.title}</h3>
                  <p>{point.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className={clsx(styles.section, styles.examplesSection)}>
          <div className="container">
            <h2 className={styles.sectionTitle}>Quelques exemples</h2>
            <p className={styles.sectionSubtitle}>
              Vingt programmes courts, du premier hello à une gestion de stock
              complète.
            </p>
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
      </main>
    </Layout>
  );
}