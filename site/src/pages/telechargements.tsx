import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';

const COMPILATEUR = [
  {plateforme: 'Linux / macOS', commande: 'curl -fsSL https://dystos.dev/install.sh | sh'},
  {plateforme: 'Windows', commande: 'irm https://dystos.dev/install.ps1 | iex'},
  {plateforme: 'Depuis les sources', commande: 'cd compiler && ./gradlew build'},
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

export default function DownloadsPage(): ReactNode {
  return (
    <Layout
      title="Téléchargements"
      description="Télécharger le compilateur Dystos et l'application Android.">
      <main className="container margin-vert--lg">
        <h1>Téléchargements</h1>
        <p>
          Tous les artefacts sont publiés par le projet. Rien n'est téléchargé
          depuis un service tiers, et aucune installation ne contacte un service
          externe.
        </p>

        <h2>Compilateur</h2>
        <table>
          <thead>
            <tr>
              <th>Plateforme</th>
              <th>Commande</th>
            </tr>
          </thead>
          <tbody>
            {COMPILATEUR.map((item) => (
              <tr key={item.plateforme}>
                <td>{item.plateforme}</td>
                <td>
                  <code>{item.commande}</code>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <h2>Application Android</h2>
        <p>
          L'application embarque le contenu et le compilateur : le parcours, la
          recherche et la vérification de code fonctionnent en mode avion. Aucune
          permission réseau n'est requise.
        </p>
        <p>
          <Link className="button button--primary" to="/telechargements">
            Versions et APK
          </Link>
        </p>

        <h2>Autres accès</h2>
        <ul>
          {OUTILS.map((outil) => (
            <li key={outil.nom}>
              <Link to={outil.lien}>{outil.nom}</Link> : {outil.detail}
            </li>
          ))}
        </ul>

        <h2>Vérifier une source</h2>
        <p>
          Chaque version publie une empreinte SHA-256. Comparez-la après
          téléchargement :
        </p>
        <pre>
          <code>sha256sum dystos-1.0.0.tar.gz</code>
        </pre>
      </main>
    </Layout>
  );
}