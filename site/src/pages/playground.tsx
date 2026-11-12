import type {ReactNode} from 'react';
import Layout from '@theme/Layout';
import Playground from '@site/src/components/Playground';

export default function PlaygroundPage(): ReactNode {
  return (
    <Layout
      title="Playground"
      description="Écrivez, analysez et exécutez du code Dystos dans le navigateur, sans installation.">
      <main style={{ padding: 0, maxWidth: '100%', width: '100%', minHeight: '100vh' }}>
        <Playground />
      </main>
    </Layout>
  );
}
