import type {ReactNode} from 'react';
import Layout from '@theme/Layout';
import Playground from '@site/src/components/Playground';

export default function PlaygroundPage(): ReactNode {
  return (
    <Layout
      title="Playground"
      description="Écrivez, analysez et exécutez du code Dystos dans le navigateur, sans installation.">
      <main className="container margin-vert--lg">
        <h1>Playground</h1>
        <p className="margin-bottom--lg">
          Écrivez du Dystos à gauche. L'analyse se fait automatiquement à
          la frappe et l'exécution se déclenche sur demande. Les
          diagnostics suivent le même format que la CLI. Rien n'est envoyé
          ailleurs.
        </p>
        <Playground />
      </main>
    </Layout>
  );
}
