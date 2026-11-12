import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Layout from '@theme/Layout';

import examplesData from '@site/static/examples/index.json';

import styles from './examples.module.css';

type Example = {
  id: string;
  nom: string;
  chapitre: string;
  code: string;
  sortie: string | null;
  note: string | null;
};

/**
 * Le catalogue vient de content/fr/exemples/, via tools/build-content.
 *
 * Le site ne doit pas garder une seconde copie des exemples : sinon le code
 * affiché ici finit par diverger de celui du compilateur et de l'application.
 */
const EXAMPLES = examplesData as Example[];

export default function ExamplesPage(): ReactNode {
  return (
    <Layout
      title="Exemples"
      description="Vingt programmes Dystos complets, du premier hello à une gestion de stock.">
      <main className="container margin-vert--lg">
        <h1>Exemples</h1>
        <p className="margin-bottom--lg">
          Vingt programmes courts et complets, extraits du dépôt du compilateur
          et validés à l'exécution. Quand le moteur de l'application mobile ne
          sait pas encore exécuter une construction, l'exemple le dit au lieu
          d'annoncer une sortie qui ne viendrait pas.
        </p>

        <div className={styles.grid}>
          {EXAMPLES.map((example) => (
            <article key={example.id} className={styles.card}>
              <header className={styles.cardHeader}>
                <h2 className={styles.cardTitle}>{example.nom}</h2>
                <span className={styles.chapter}>{example.chapitre}</span>
              </header>
              <CodeBlock language="dystos">{example.code}</CodeBlock>
              {example.sortie && (
                <pre className={styles.output}>
                  <span className={styles.outputLabel}>sortie</span>
                  {example.sortie}
                </pre>
              )}
              {!example.sortie && example.note && (
                <p className={styles.note}>
                  <span className={styles.noteLabel}>Dans l'application</span>
                  {example.note}
                </p>
              )}
              <Link
                className={styles.openInPlayground}
                to={`/playground?exemple=${example.id}`}>
                Ouvrir dans le playground
              </Link>
            </article>
          ))}
        </div>
      </main>
    </Layout>
  );
}
