import type {ReactNode} from 'react';
import Layout from '@theme/Layout';
import Playground from '@site/src/components/Playground';

export default function PlaygroundPage(): ReactNode {
  return (
    <Layout
      title="Playground"
      description="Écrivez, analysez et exécutez du code Dystos dans le navigateur, sans installation.">
      <main className="container margin-vert--lg">
        <div className="row">
          <div className="col col--12">
            <h1>Playground</h1>
            <p className="margin-bottom--lg">
              Écris du Dystos à gauche. L'analyse et l'exécution vont vers le
              service de playground, qui renvoie les diagnostics au même format
              que la CLI. Rien n'est envoyé ailleurs.
            </p>
            <Playground />
          </div>
        </div>
      </main>
    </Layout>
  );
}