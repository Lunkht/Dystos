import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Layout from '@theme/Layout';

import styles from './examples.module.css';

type Example = {
  id: string;
  nom: string;
  chapitre: string;
  code: string;
  sortie?: string;
};

const EXAMPLES: Example[] = [
  {
    id: '01-hello-world',
    nom: 'Hello world',
    chapitre: 'Premiers pas',
    code: 'print("Bonjour, Dystos !")',
    sortie: 'Bonjour, Dystos !',
  },
  {
    id: '02-variables',
    nom: 'Variables',
    chapitre: 'Premiers pas',
    code: `val langage: string = "Dystos"
var compteur: int = 0

compteur += 1
print(f"{langage} {compteur}")
`,
    sortie: 'Dystos 1',
  },
  {
    id: '03-fonctions',
    nom: 'Fonctions',
    chapitre: 'Premiers pas',
    code: `def prix_ttc(ht: float, tva: float = 0.20) -> float:
    return ht * (1.0 + tva)

print(prix_ttc(100.0))
`,
    sortie: '120.0',
  },
  {
    id: '04-conditions',
    nom: 'Conditions',
    chapitre: 'Contrôle',
    code: `val note: int = 14

if note >= 16:
    print("très bien")
elif note >= 12:
    print("bien")
else:
    print("à revoir")
`,
    sortie: 'bien',
  },
  {
    id: '05-collections',
    nom: 'Collections',
    chapitre: 'Données',
    code: `val fruits = ["pomme", "banane", "kiwi"]
fruits.append("orange")

print(fruits[0], len(fruits))
`,
    sortie: 'pomme 4',
  },
  {
    id: '06-comprehensions',
    nom: 'Compréhensions',
    chapitre: 'Données',
    code: `val nombres = range(1, 11)
print([n * n for n in nombres if n % 2 == 0])
`,
    sortie: '[4, 16, 36, 64, 100]',
  },
  {
    id: '07-f-strings',
    nom: 'Chaînes formatées',
    chapitre: 'Données',
    code: `val nom = "Awa"
val age: int = 20
print(f"{nom} aura {age + 1} ans l'an prochain")
`,
    sortie: 'Awa aura 21 ans lan prochain',
  },
  {
    id: '08-classes',
    nom: 'Classes et constructeurs',
    chapitre: 'Objets',
    code: `class Compte:
    _solde: float = 0.0

    def __init__(self, nom: string):
        self.nom = nom

    def deposer(self, montant: float) -> float:
        self._solde += montant
        return self._solde

    def __str__(self) -> string:
        return f"{self.nom} ({self._solde:.2f})"

print(Compte("Awa").deposer(150.0))
`,
    sortie: '150.0',
  },
  {
    id: '09-heritage',
    nom: 'Héritage',
    chapitre: 'Objets',
    code: `class Compte:
    def __init__(self, nom: string):
        self.nom = nom

class CompteEpargne(Compte):
    def __init__(self, nom: string, taux: float):
        super.__init__(nom)
        self.taux = taux

    def projected(self) -> float:
        return 1000.0 * (1.0 + self.taux)

print(CompteEpargne("Moussa", 0.03).projected())
`,
    sortie: '1030.0',
  },
  {
    id: '10-generiques',
    nom: 'Génériques',
    chapitre: 'Objets',
    code: `class Pile<T>:
    _elements: list<T> = []

    def ajouter(self, element: T):
        self._elements.append(element)

    def depiler(self) -> T:
        return self._elements.pop()

var pile = Pile<string>()
pile.ajouter("a")
pile.ajouter("b")
print(pile.depiler())
`,
    sortie: 'b',
  },
  {
    id: '11-null-safety',
    nom: 'Null-sûreté',
    chapitre: 'Sûreté',
    code: `val alias: string? = null
print(alias?.len() ?? 0)

alias = "Dystos"
print(alias?.len() ?? 0)
`,
    sortie: '0\n6',
  },
  {
    id: '12-exceptions',
    nom: 'Exceptions',
    chapitre: 'Sûreté',
    code: `def diviser(a: float, b: float) -> float:
    if b == 0.0:
        throw ArithmeticError("division par zéro")
    return a / b

try:
    print(diviser(1.0, 0.0))
except ArithmeticError as e:
    print("erreur :", e.message)
`,
    sortie: 'erreur : division par zéro',
  },
  {
    id: '13-lambdas',
    nom: 'Fonctions d’ordre supérieur',
    chapitre: 'Fonctionnel',
    code: `val nombres = [1, 2, 3, 4, 5]
print(nombres.map((n) => n * 2))
print(nombres.filter((n) => n % 2 == 1))
print(nombres.reduce((a, n) => a + n, 0))
`,
    sortie: '[2, 4, 6, 8, 10]\n[1, 3, 5]\n15',
  },
  {
    id: '14-enum-match',
    nom: 'Enum et match',
    chapitre: 'Structurer',
    code: `enum Statut:
    ACTIF
    SUSPENDU

def etiquette(s: Statut) -> string:
    match s:
        case Statut.ACTIF:
            return "actif"
        case Statut.SUSPENDU:
            return "suspendu"
        case _:
            return "inconnu"

print(etiquette(Statut.SUSPENDU))
`,
    sortie: 'suspendu',
  },
  {
    id: '15-fichiers',
    nom: 'Fichiers',
    chapitre: 'Monde réel',
    code: `val fichier = open("notes.txt", mode: "w")
fichier.write("12\\n15\\n18")
fichier.close()

for ligne in open("notes.txt").lines():
    print(ligne.trim())
`,
    sortie: '12\n15\n18',
  },
  {
    id: '16-interop-java',
    nom: 'Interop Java',
    chapitre: 'Monde réel',
    code: `import java.util.ArrayList

var liste = ArrayList<String>()
liste.add("Dystos")

for element in liste:
    print(element)
`,
    sortie: 'Dystos',
  },
  {
    id: '17-concurrence',
    nom: 'Concurrence',
    chapitre: 'Monde réel',
    code: `import java.util.concurrent.CompletableFuture

var tache = CompletableFuture.supplyAsync({ -> 21 * 2 })
print(tache.join())
`,
    sortie: '42',
  },
  {
    id: '18-packages',
    nom: 'Packages et visibilité',
    chapitre: 'Structurer',
    code: `package stock

class Depot:
    _interne: int = 0

    def incrementer(self, n: int = 1):
        self._interne += n
        return self._interne

print(Depot().incrementer(5))
`,
    sortie: '5',
  },
  {
    id: '19-tests',
    nom: 'Tests intégrés',
    chapitre: 'Qualité',
    code: `def addition(a: int, b: int) -> int:
    return a + b

test addition:
    assert addition(2, 2) == 4
    assert addition(-1, 1) == 0

print("tous les tests passent")
`,
    sortie: 'tous les tests passent',
  },
  {
    id: '20-gestion-stock',
    nom: 'Gestion de stock',
    chapitre: 'Projet',
    code: `class Article:
    def __init__(self, nom: string, prix: float):
        self.nom = nom
        self.prix = prix

class Depot:
    def __init__(self):
        self._articles: dict<string, int> = {}

    def ajouter(self, article: Article, qte: int):
        self._articles[article.nom] = self._articles.get(article.nom, 0) + qte

    def total(self) -> float:
        return float(sum(qte for qte in self._articles.values()))

val depot = Depot()
depot.ajouter(Article("clavier", 25.0), 4)
depot.ajouter(Article("souris", 12.5), 10)

for nom, qte in depot._articles.items():
    print(f"{nom} : {qte}")

print("unités :", depot.total())
`,
    sortie: 'clavier : 4\nsouris : 10\nunités : 14.0',
  },
];

export default function ExamplesPage(): ReactNode {
  return (
    <Layout
      title="Exemples"
      description="Vingt programmes Dystos complets, du premier hello à une gestion de stock.">
      <main className="container margin-vert--lg">
        <h1>Exemples</h1>
        <p className="margin-bottom--lg">
          Vingt programmes courts et complets. Chaque exemple est validé par le
          compilateur au build : si le langage évolue et casse un exemple, le
          build échoue.
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