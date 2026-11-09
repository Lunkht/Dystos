# Phase 0 — Décisions de syntaxe et 20 programmes d'exemple

*Langage : **Dystos**. Extension de fichier : `.dys`.*

Ces exemples servent de **cahier des charges** : à la fin de la Phase 1, le parser doit les accepter tous, et en Phase 3 chacun doit produire la sortie indiquée.

---

## A. Décisions de syntaxe

Décision validée : **`self` explicite**, comme en Python. Elle corrige l'incohérence de la spec v0.1.

| Sujet | Décision |
|---|---|
| Instance courante | `self` explicite, premier paramètre de chaque méthode d'instance. Les fonctions et les méthodes `@static` n'en ont pas |
| Constructeur | `def init(self, ...)` dans la classe |
| Champs | Déclarés dans le corps (`nom: str`), accessibles via `self.nom` |
| Visibilité | Public par défaut, préfixe `_` = privé |
| Point d'entrée | `def main()` dans le fichier exécuté |
| Constantes | `val x = 5` (immuable), `var x = 5` ou `x = 5` (mutable) |
| Nullable | `T?`, accès sûr `?.`, valeur par défaut `?:` |
| Lambdas | `x -> x + 1`, `(a, b) -> a + b` |
| Commentaires | `# ligne` |
| Égalité | `==` compare les valeurs (comme `equals`), `is` compare les références |

---

## B. Les 20 programmes

### 01 — Hello world
*Teste : point d'entrée, `print`.*
```
def main():
    print("Bonjour le monde")
```
Sortie : `Bonjour le monde`

### 02 — Variables et inférence
*Teste : inférence, `val`/`var`, types explicites.*
```
def main():
    val nom = "Lunkht"
    var age = 30
    age = age + 1
    taille: float = 1.75
    actif = true
    print(f"{nom} a {age} ans, {taille} m, actif={actif}")
```
Sortie : `Lunkht a 31 ans, 1.75 m, actif=true`

### 03 — Fonctions
*Teste : paramètres typés, défauts, arguments nommés.*
```
def saluer(nom: str, titre: str = "M.") -> str:
    return f"Bonjour {titre} {nom}"

def main():
    print(saluer("Diallo"))
    print(saluer("Martin", titre="Dr"))
```
Sortie : `Bonjour M. Diallo` puis `Bonjour Dr Martin`

### 04 — Conditions et boucles
*Teste : `if/elif/else`, `for`, `while`, `range`, `break`, `continue`.*
```
def main():
    for i in range(1, 16):
        if i % 15 == 0:
            print("FizzBuzz")
        elif i % 3 == 0:
            print("Fizz")
        elif i % 5 == 0:
            print("Buzz")
        else:
            print(i)

    n = 0
    while true:
        n += 1
        if n == 3:
            continue
        if n > 5:
            break
        print(n)
```

### 05 — Collections
*Teste : `list`, `map`, `set`, accès, ajout, itération.*
```
def main():
    fruits: list[str] = ["pomme", "poire"]
    fruits.add("kiwi")
    print(fruits[0], len(fruits))

    stock: map[str, int] = {"pomme": 3, "poire": 5}
    stock["kiwi"] = 7
    for nom, qte in stock.items():
        print(f"{nom}: {qte}")

    uniques = {1, 2, 2, 3}
    print(len(uniques))
```
Sortie finale : `3`

### 06 — Compréhensions
*Teste : listes, maps et sets en compréhension avec filtre.*
```
def main():
    carres = [x * x for x in range(10) if x % 2 == 0]
    longueurs = {mot: len(mot) for mot in ["a", "bcd", "ef"]}
    pairs = {x for x in range(6) if x % 2 == 0}
    print(carres, longueurs, pairs)
```

### 07 — Chaînes et f-strings
*Teste : formatage, méthodes, tranches (slices).*
```
def main():
    s = "Développeur"
    print(s.upper(), s.length, s[0:4])
    pi = 3.14159265
    print(f"{pi:.2f} | {42:05d} | {'ok' if pi > 3 else 'ko'}")
    lignes = "a,b,c".split(",")
    print("-".join(lignes))
```

### 08 — Classes et constructeurs
*Teste : champs, `init`, méthodes, `self`, `toString`.*
```
class Compte:
    titulaire: str
    _solde: float = 0.0

    def init(self, titulaire: str):
        self.titulaire = titulaire

    def deposer(self, montant: float):
        self._solde += montant

    def solde(self) -> float:
        return self._solde

    def toString(self) -> str:
        return f"Compte({self.titulaire}, {self._solde})"

def main():
    c = Compte("Awa")
    c.deposer(150.0)
    print(c)
```
Sortie : `Compte(Awa, 150.0)`

### 09 — Héritage et interfaces
*Teste : `extends`, `implements`, polymorphisme, `abstract`.*
```
interface Forme:
    def aire(self) -> float

abstract class Base(Forme):
    nom: str
    def init(self, nom: str):
        self.nom = nom

class Carre(Base):
    cote: float
    def init(self, cote: float):
        super().init("carré")
        self.cote = cote
    def aire(self) -> float:
        return self.cote * self.cote

def main():
    formes: list[Forme] = [Carre(2.0), Carre(3.0)]
    total = sum(f.aire() for f in formes)
    print(total)
```
Sortie : `13.0`

### 10 — Génériques
*Teste : classes et fonctions génériques.*
```
class Boite[T]:
    contenu: T
    def init(self, contenu: T):
        self.contenu = contenu
    def get(self) -> T:
        return self.contenu

def premier[T](items: list[T]) -> T:
    return items[0]

def main():
    b = Boite[int](42)
    print(b.get(), premier(["x", "y"]))
```

### 11 — Null-safety
*Teste : `T?`, `?.`, `?:`, refus de `null` sur un type non nullable.*
```
def longueur(s: str?) -> int:
    return s?.length ?: 0

def main():
    print(longueur("abc"), longueur(null))
    x: str = null     # ERREUR DE COMPILATION attendue
```
Attendu : la dernière ligne est rejetée avec un message clair (ligne, colonne, suggestion `str?`).

### 12 — Exceptions
*Teste : `try/except/finally`, `raise`, exceptions personnalisées.*
```
class SoldeInsuffisant(Exception):
    def init(self, manque: float):
        super().init(f"Il manque {manque}")

def retirer(solde: float, montant: float) -> float:
    if montant > solde:
        raise SoldeInsuffisant(montant - solde)
    return solde - montant

def main():
    try:
        retirer(10.0, 25.0)
    except SoldeInsuffisant as e:
        print("Erreur :", e.message)
    finally:
        print("Fin")
```

### 13 — Lambdas et ordre supérieur
*Teste : fonctions comme valeurs, `map`/`filter`/`sorted`.*
```
def appliquer(f: (int) -> int, valeurs: list[int]) -> list[int]:
    return [f(v) for v in valeurs]

def main():
    doubles = appliquer(x -> x * 2, [1, 2, 3])
    pairs = filter(x -> x % 2 == 0, doubles)
    tries = sorted(["bb", "a", "ccc"], key=s -> s.length)
    print(doubles, pairs, tries)
```

### 14 — Enum et `match`
*Teste : énumérations, filtrage par motif.*
```
enum Jour:
    LUNDI, MARDI, SAMEDI, DIMANCHE

def type_jour(j: Jour) -> str:
    match j:
        case Jour.SAMEDI | Jour.DIMANCHE:
            return "week-end"
        case _:
            return "semaine"

def main():
    print(type_jour(Jour.SAMEDI))
```

### 15 — Fichiers (I/O hors ligne)
*Teste : lecture/écriture locale, `with` (fermeture automatique).*
```
import io

def main():
    with io.open("notes.txt", "w") as f:
        f.write("ligne 1\nligne 2\n")
    with io.open("notes.txt") as f:
        for ligne in f.lines():
            print(ligne.strip())
```

### 16 — Interop Java
*Teste : import et usage direct de classes Java, sans wrapper.*
```
import java.util.ArrayList
import java.time.LocalDate

def main():
    liste = ArrayList[str]()
    liste.add("a")
    liste.add("b")
    print(liste.size(), LocalDate.now().year)
```

### 17 — Concurrence
*Teste : threads virtuels, attente de résultats.*
```
import concurrent

def calcul(n: int) -> int:
    return n * n

def main():
    taches = [concurrent.run(() -> calcul(i)) for i in range(4)]
    resultats = [t.await() for t in taches]
    print(resultats)
```
Sortie : `[0, 1, 4, 9]`

### 18 — Packages et visibilité
*Teste : `package`, `import`, membre privé inaccessible.*
```
# fichier app/outils.dys
package app

def _secret() -> int:
    return 42

def public() -> int:
    return _secret() + 1
```
```
# fichier main.dys
import app.outils

def main():
    print(outils.public())    # 43
    print(outils._secret())   # ERREUR DE COMPILATION attendue
```

### 19 — Tests intégrés
*Teste : annotations `@test`, assertions, lanceur `test`.*
```
def additionner(a: int, b: int) -> int:
    return a + b

@test
def test_addition():
    assert additionner(2, 3) == 5
    assert additionner(-1, 1) == 0
```
Commande : `dystos test` → `1 test réussi`

### 20 — Programme complet : gestion de stock
*Teste : tout ensemble (classes, collections, null-safety, exceptions, fichiers).*
```
class Produit:
    nom: str
    prix: float
    quantite: int

    def init(self, nom: str, prix: float, quantite: int = 0):
        self.nom = nom
        self.prix = prix
        self.quantite = quantite

    def valeur(self) -> float:
        return self.prix * self.quantite

class Stock:
    _produits: map[str, Produit] = {}

    def ajouter(self, p: Produit):
        self._produits[p.nom] = p

    def trouver(self, nom: str) -> Produit?:
        return self._produits.get(nom)

    def total(self) -> float:
        return sum(p.valeur() for p in self._produits.values())

def main():
    stock = Stock()
    stock.ajouter(Produit("riz", 1.5, 100))
    stock.ajouter(Produit("huile", 3.2, 40))

    p = stock.trouver("riz")
    print(p?.nom ?: "introuvable")
    print(stock.trouver("sucre")?.nom ?: "introuvable")
    print(f"Valeur totale : {stock.total():.2f}")
```
Sortie : `riz`, `introuvable`, `Valeur totale : 278.00`

---

## C. Couverture des fonctionnalités

| Fonctionnalité | Programmes |
|---|---|
| Syntaxe de base, inférence | 01, 02, 03, 04 |
| Collections et compréhensions | 05, 06, 07 |
| POO | 08, 09, 10, 14 |
| Sûreté (null, exceptions) | 11, 12 |
| Fonctionnel | 13 |
| E/S et interop JVM | 15, 16 |
| Concurrence | 17 |
| Modularité et tests | 18, 19 |
| Intégration | 20 |

## D. Décisions validées

1. `val` / `var` sont conservés, en plus de l'affectation simple (`x = 5`).
2. Les exemples 11 (null sur type non nullable) et 18 (membre privé) sont des **erreurs de compilation**, pas des avertissements.
3. `print(x, y)` sépare les arguments par un espace, comme en Python.

**Phase 0 terminée.** Prochaine étape : Phase 1 (lexer avec gestion de l'indentation, puis parser et AST).
