# Dystos — Architecture et plan de développement

*Dystos : langage à syntaxe lisible type Python, typage et modèle objet type Java, exécuté sur la JVM.*

**Dépôt** : https://github.com/Lunkht/Dystos.git

---

## 1. Vision et principes

**Objectif** : combiner la concision de Python avec la robustesse de Java.

| Vient de Python | Vient de Java |
|---|---|
| Syntaxe par indentation, pas de `;` ni `{}` | Typage statique, vérifié à la compilation |
| Inférence de types (`x = 5`) | Classes, interfaces, packages, génériques |
| Compréhensions de listes, f-strings | Performance via JVM (JIT), bytecode portable |
| REPL / exécution directe de scripts | Écosystème de bibliothèques Java (interop totale) |
| Valeurs par défaut, arguments nommés | Gestion mémoire par GC, threads |

**Principes de conception**
1. Lisibilité d'abord : un programme doit se lire comme du pseudo-code.
2. Typage statique avec inférence : types écrits seulement quand ils apportent de la clarté.
3. Interop Java 100 % : appeler n'importe quelle classe Java sans wrapper.
4. Fonctionnement 100 % hors ligne, sans dépendance à un service tiers.
5. Erreurs de compilation claires, avec ligne, colonne et suggestion.

---

## 2. Spécification du langage (v0.1)

### 2.1 Exemple de syntaxe

```
package app.demo

import java.util.List

interface Forme:
    def aire(self) -> float

class Cercle(Forme):
    rayon: float

    def init(self, rayon: float):
        self.rayon = rayon

    def aire(self) -> float:
        return 3.14159 * self.rayon ** 2

def main():
    formes = [Cercle(2.0), Cercle(3.5)]
    for f in formes:
        print(f"Aire : {f.aire():.2f}")

    carres = [x * x for x in range(10) if x % 2 == 0]
```

### 2.2 Choix de conception

- **Blocs** : indentation (4 espaces), `:` en fin de ligne.
- **Types** : `int`, `float`, `bool`, `str`, `list[T]`, `map[K, V]`, `set[T]`, `T?` (nullable).
- **Inférence locale** : `x = 10` → `int`. Types obligatoires sur les signatures publiques.
- **Null-safety** : `T` jamais null, `T?` nullable, opérateur `?.`.
- **POO** : classes, héritage simple, interfaces multiples, `abstract`, `final`.
- **Instance courante** : `self` explicite, premier paramètre de chaque méthode d'instance (comme en Python). Compilé vers `this` (variable locale 0) dans le bytecode.
- **Visibilité** : `public` par défaut ; préfixe `_` = privé (vérifié par le compilateur).
- **Fonctions** : arguments nommés, valeurs par défaut, lambdas (`x -> x + 1`).
- **Erreurs** : `try / except / finally` mappé sur les exceptions Java.
- **Concurrence** : `async`/threads reposant sur les threads Java (virtual threads, JDK 21+).
- **Mots-clés réservés (v0.1)** : `def class interface import package if elif else for while return try except finally match`.

### 2.3 Grammaire (extrait EBNF)

```
file        = [package_decl] {import_decl} {top_decl}
top_decl    = class_decl | interface_decl | func_decl
class_decl  = "class" NAME ["(" NAME {"," NAME} ")"] ":" NEWLINE INDENT {member} DEDENT
func_decl   = "def" NAME "(" [params] ")" ["->" type] ":" block
block       = NEWLINE INDENT {statement} DEDENT
statement   = assign | if_stmt | for_stmt | while_stmt | return_stmt | expr
```

---

## 3. Architecture du compilateur

### 3.1 Vue d'ensemble

```
 Source .dys
      │
      ▼
 ┌──────────┐   tokens   ┌──────────┐    AST    ┌──────────────┐
 │  Lexer   │──────────▶│  Parser  │─────────▶│  Analyse     │
 │ INDENT/  │            │ (récursif│           │  sémantique  │
 │ DEDENT   │            │ descend.)│           │ (symboles,   │
 └──────────┘            └──────────┘           │  types)      │
                                                └──────┬───────┘
                                                       │ AST typé
                                                       ▼
                                                ┌──────────────┐
                                                │  IR (SSA     │
                                                │  simplifié)  │
                                                └──────┬───────┘
                                      ┌────────────────┴───────────────┐
                                      ▼                                ▼
                              ┌───────────────┐                ┌──────────────┐
                              │ Backend JVM   │                │ Interpréteur │
                              │ (bytecode     │                │ (REPL, tests)│
                              │  via ASM)     │                └──────────────┘
                              └───────┬───────┘
                                      ▼
                                 .class / .jar
```

### 3.2 Modules

| Module | Rôle | Détails |
|---|---|---|
| `dystos-lexer` | Analyse lexicale | Gère l'indentation (pile d'indentation → tokens `INDENT`/`DEDENT`), littéraux, f-strings, commentaires `#` |
| `dystos-parser` | Analyse syntaxique | Descente récursive + Pratt parser pour les expressions ; produit l'AST ; récupération d'erreurs |
| `dystos-ast` | Structures de l'AST | Nœuds immuables, visiteur, positions source |
| `dystos-sema` | Analyse sémantique | Table des symboles, résolution des noms, inférence de types (Hindley-Milner local), vérif. null-safety |
| `dystos-interop` | Pont Java | Lecture des `.class` / réflexion pour résoudre les types Java, mapping `list` ↔ `java.util.List` |
| `dystos-ir` | Représentation intermédiaire | Instructions simples, optimisations (constant folding, dead code) |
| `dystos-backend-jvm` | Génération | Bytecode via la bibliothèque **ASM**, génération de `.class` et `.jar` |
| `dystos-interp` | Interpréteur | Exécution directe de l'IR pour le REPL |
| `dystos-runtime` | Bibliothèque standard | `print`, `range`, collections, I/O, texte, maths |
| `dystos-cli` | Outils | `dystos run`, `build`, `repl`, `test`, `fmt` |
| `dystos-lsp` | Support éditeur | Serveur LSP : coloration, diagnostics, autocomplétion |

### 3.3 Choix techniques

- **Langage d'implémentation** : Java 21 (maîtrise de l'écosystème, ASM, outillage JVM).
- **Génération de bytecode** : ASM (pas de dépendance réseau, embarquable).
- **Build** : Gradle ou Maven, dépôts de dépendances miroités en local (build hors ligne).
- **Tests** : JUnit 5 + tests « golden files » (source → sortie attendue).
- **Auto-hébergement (long terme)** : réécrire le compilateur en Dystos lui-même.

### 3.4 Correspondance Dystos → JVM

| Dystos | Bytecode / Java |
|---|---|
| `class A(B, I)` | `class A extends B implements I` |
| `self` (1er paramètre) | `this` (slot 0), retiré de la signature JVM |
| `list[int]` | `java.util.List<Integer>` |
| `x: int` local | `int` primitif (boxing automatique si générique) |
| `T?` | Référence nullable + vérifs injectées |
| `f"..."` | `StringBuilder` / `invokedynamic` |
| Compréhension de liste | Boucle générée + `ArrayList` |
| Lambda | `invokedynamic` + `LambdaMetafactory` |
| `try/except` | Table d'exceptions du bytecode |

---

## 4. Plan de développement

### Phase 0 — Cadrage (2 semaines)
- Finaliser la spécification v0.1 (mots-clés, types, grammaire).
- Écrire 20 programmes d'exemple servant de cahier des charges.
- Mettre en place le dépôt, le build et le CI local.

**Livrable** : document de spécification + exemples.

### Phase 1 — Front-end (4 semaines)
- Lexer avec gestion de l'indentation.
- Parser complet + AST + messages d'erreur avec position.
- Afficheur d'AST (`dystos ast fichier.dys`) pour déboguer.

**Livrable** : parsing de tous les exemples. **Critère** : 100 % des exemples valides parsés, erreurs claires sur les invalides.

### Phase 2 — Analyse sémantique (5 semaines)
- Table des symboles, portées, résolution des noms.
- Système de types : primitifs, classes, génériques simples, inférence locale.
- Null-safety (`T?`), vérification des interfaces.
- Interop : charger les types Java (`java.util.*`).

**Livrable** : AST typé. **Critère** : rejette les erreurs de type, accepte les programmes corrects.

### Phase 3 — Backend JVM (6 semaines)
- IR minimale puis génération de bytecode (ASM).
- Expressions, contrôle de flux, fonctions, classes, interfaces.
- Exceptions, lambdas, compréhensions, f-strings.
- Packaging `.jar` exécutable.

**Livrable** : `dystos build` produit un `.jar` qui s'exécute avec `java -jar`.

### Phase 4 — Bibliothèque standard et runtime (4 semaines)
- `print`, `input`, `range`, `len`, `enumerate`, `zip`.
- Collections, fichiers, texte, date/heure, JSON.
- Documentation de chaque fonction.

### Phase 5 — Outillage développeur (5 semaines)
- REPL (interpréteur sur l'IR).
- `dystos test` (framework de tests intégré), `dystos fmt` (formateur).
- Serveur LSP + extension d'éditeur (coloration, diagnostics, autocomplétion).
- Gestionnaire de paquets local (`dystos.toml`, dépôt auto-hébergé).

### Phase 6 — Optimisation et stabilisation (4 semaines)
- Optimisations de l'IR, benchmarks vs Java et Python.
- Suite de tests de non-régression, fuzzing du parser.
- Documentation utilisateur complète, version **1.0-beta**.

### Phase 7 — Évolutions (continu)
- Pattern matching (`match`), types algébriques, génériques avancés.
- Backend alternatif (Android/DEX, natif via GraalVM).
- Auto-hébergement du compilateur.

### Calendrier

| Phase | Durée | Cumul |
|---|---|---|
| 0 Cadrage | 2 sem. | 2 |
| 1 Front-end | 4 sem. | 6 |
| 2 Sémantique | 5 sem. | 11 |
| 3 Backend JVM | 6 sem. | 17 |
| 4 Runtime | 4 sem. | 21 |
| 5 Outillage | 5 sem. | 26 |
| 6 Stabilisation | 4 sem. | 30 |

Estimation pour une personne à temps plein : **~7 mois** jusqu'à la bêta. À temps partiel, prévoir le double.

---

## 5. Structure du dépôt

```
dystos/
├── spec/                 # Spécification du langage
├── compiler/
│   ├── lexer/
│   ├── parser/
│   ├── ast/
│   ├── sema/
│   ├── interop/
│   ├── ir/
│   ├── backend-jvm/
│   └── interp/
├── runtime/              # Bibliothèque standard
├── cli/
├── lsp/
├── examples/
├── tests/
│   ├── golden/
│   └── unit/
└── docs/
```

---

## 6. Stratégie de tests

- **Unitaires** : chaque module (lexer, parser, sema) isolément.
- **Golden files** : `exemple.dys` → `exemple.expected`.
- **Compilation bout en bout** : compiler, exécuter, comparer la sortie.
- **Erreurs** : fichiers invalides avec message attendu.
- **Performance** : micro-benchmarks suivis à chaque release.

---

## 7. Risques et mitigations

| Risque | Impact | Mitigation |
|---|---|---|
| Indentation ambiguë dans le lexer | Moyen | Pile d'indentation stricte, tabs interdits |
| Inférence de types trop complexe | Élevé | Limiter à l'inférence locale en v1 |
| Interop Java (génériques, surcharge) | Élevé | Commencer par un sous-ensemble ; tests sur `java.util` |
| Dérive du périmètre | Élevé | Gel de la spec v0.1, nouveautés → Phase 7 |
| Messages d'erreur pauvres | Moyen | Positions dans l'AST dès la Phase 1 |

---

## 8. Documentation à produire

1. **Guide du langage** (tutoriel progressif, 10 chapitres).
2. **Référence** (grammaire, types, mots-clés, bibliothèque standard).
3. **Guide d'interop Java**.
4. **Documentation du compilateur** (architecture, comment ajouter une construction).
5. **Guide de contribution** et conventions de code.

---

## 9. Prochaines étapes

1. Valider les choix clés : indentation, `T?`, héritage simple, cible JVM.
2. Écrire les 20 programmes d'exemple.
3. Démarrer la Phase 1 : lexer + parser sur un sous-ensemble (variables, fonctions, `if`, `for`).
