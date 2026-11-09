# Dystos — Guide du projet et marche à suivre

*Ce document résume tout ce qui a été construit, explique le code, et décrit pas à pas la suite du développement. Il est écrit pour que tu puisses continuer seul, sur ta machine, sans l'historique de la conversation.*

Dépôt : https://github.com/Lunkht/Dystos.git

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [État d'avancement](#2-état-davancement)
3. [Contenu de l'archive](#3-contenu-de-larchive)
4. [Installer et vérifier sur ta machine](#4-installer-et-vérifier-sur-ta-machine)
5. [Chronologie de ce qui a été fait](#5-chronologie-de-ce-qui-a-été-fait)
6. [Décisions de conception](#6-décisions-de-conception)
7. [Architecture du code](#7-architecture-du-code)
8. [Recettes : modifier le langage](#8-recettes--modifier-le-langage)
9. [Feuille de route pas à pas](#9-feuille-de-route-pas-à-pas)
10. [Contrats d'interface](#10-contrats-dinterface)
11. [Limites connues et backlog](#11-limites-connues-et-backlog)
12. [Reprendre avec Claude](#12-reprendre-avec-claude)
13. [Aide-mémoire](#13-aide-mémoire)

---

## 1. Vue d'ensemble

**Dystos** est un langage de programmation qui mélange la lisibilité de Python (blocs par indentation, inférence de types, f-strings, compréhensions) et la rigueur de Java (typage statique, classes, interfaces, génériques, interopérabilité totale avec les bibliothèques Java). Il est implémenté en **Java** et vise la **JVM**.

```
package app.demo

interface Forme:
    def aire(self) -> float

class Cercle(Forme):
    rayon: float

    def init(self, rayon: float):
        self.rayon = rayon

    def aire(self) -> float:
        return 3.14159 * self.rayon ** 2

def main():
    formes: list[Forme] = [Cercle(2.0), Cercle(3.5)]
    for f in formes:
        print(f"Aire : {f.aire():.2f}")

    carres = [x * x for x in range(10) if x % 2 == 0]
    print(carres)
    nom: str? = null
    print(nom?.length ?: 0)
```

Principes : **souveraineté** (aucune dépendance à un service tiers), **hors ligne**, **autohébergé**, messages d'erreur clairs en français avec ligne et colonne.

Extension de fichier : `.dys`. Nom du paquet Java : `com.vulsoft.dystos`.

---

## 2. État d'avancement

| Étape | Contenu | Statut |
|---|---|---|
| Phase 0 | Spécification v0.1, 20 programmes d'exemple, décisions de syntaxe | ✅ terminée |
| Phase 1 | Lexer (indentation, f-strings), parser, AST, scripts | ✅ terminée |
| Phase 2 | Analyse sémantique : noms, types, null-safety, classes, interop Java, diagnostics JSON | ✅ terminée |
| *Interpréteur d'AST* | Exécution des programmes, mode sandbox, CLI (`run`, `test`) — *inséré avant la Phase 3* | ✅ terminé |
| *Étape 2 du plan* | Colorateur tolérant, grammaires **TextMate** et **Prism**, extension VS Code, **service playground** (API HTTP `analyze` et `run`) | ✅ terminée |
| Phase 3 — palier 3a | **Backend JVM** : bytecode et `.jar` autonome pour le cœur du langage (compilateur, écrivain de classes, runtime) | ✅ terminé |
| Phase 3 — palier 3b | lambdas et fermetures, `io`, `concurrent` (vrais threads), interopérabilité Java, globales de scripts, méthodes « pont », méthodes par défaut d'interface, numéros de ligne | ✅ terminé |
| Phase 3 — suite | tableaux Java, `String.format` (arguments variables), classes de version ≥ 52, `@test` compilé, mesures de performance | ⏳ à faire |
| Phase 4 | Bibliothèque standard | ⏳ à faire |
| Phase 5 | Outillage : REPL, formateur, LSP, gestionnaire de paquets | ⏳ à faire |
| Phase 6 | Stabilisation, performance, documentation, 1.0-bêta | ⏳ à faire |
| En parallèle | Site web d'apprentissage et application Android (plan fourni, développement par toi) | ⏳ en cours côté toi |

**Vérifié avant cet export**
- **281 tests** unitaires passent avec **JUnit 5.10** (lexer 39, parser 47, sémantique 61, interpréteur 36, runtime 11, backend JVM 61, playground 26), dont des tests **différentiels** (interpréteur contre bytecode exécuté dans une vraie JVM), de vraies requêtes HTTP et le mode « processus fils ».
- **Les 20 exemples compilent en bytecode** ; sur les 19 exécutables, le bytecode et l'interpréteur donnent exactement la même sortie et le même code de sortie (l'exemple 11 est refusé par les deux avec les mêmes diagnostics). Les erreurs non rattrapées ont le même message **et le même numéro de ligne**.
- **19 contrôles** automatiques des grammaires (`scripts/test-editors.sh`) avec le vrai moteur TextMate de VS Code (vscode-textmate + Oniguruma) et avec Prism.
- Les 4 modules compilent en **Java 21** et aussi en **Java 17**.
- Les 19 exemples exécutables produisent les sorties attendues ; les exemples 11 (null) et 18 (membre privé) sont refusés comme prévu.

**Vérifié depuis (mise à jour)**
- Le build **Maven** (`mvn test`) : **281 tests verts** (7 modules + racine), avec Maven 3.9.9 et JDK 21.
- Le fonctionnement sous **Windows natif** (sans WSL2) : compilation, CLI, bytecode et les 20 exemples via `scripts/build.ps1`, `scripts/dystos.ps1` et `scripts/run-examples.ps1`. Seule adaptation nécessaire : la comparaison des sorties de processus ignore désormais les fins de ligne CRLF/LF (`JvmTest`).

**Non vérifié (à faire chez toi en premier)**
- L'embarquement sur **Android** (réflexion Java, fil d'exécution dédié).
- **Docker** (`Dockerfile`, `docker-compose.yml`, `Caddyfile`) : écrits mais jamais lancés (Docker n'était pas disponible).
- L'installation de l'extension dans **VS Code** et l'intégration dans **Docusaurus** (seul le moteur de coloration est vérifié).
- Les **performances** ne sont mesurées que sur quatre petites charges (`scripts/bench.sh`, une mesure chacune, une seule machine) : bytecode ≈ 75 à 95× (récursion), ≈ 50× (boucles), ≈ 30 à 40× (objets), ≈ 2,5× (listes encadrées) plus rapide que l'interpréteur. Ce sont des ordres de grandeur.

---

## 3. Contenu de l'archive

```
Dystos/
├── README.md                    # démarrage rapide
├── .gitignore  .gitattributes   # fins de ligne LF (important sous Windows)
├── docs/
│   ├── GUIDE-PROJET.md          # ce document
│   ├── 01-architecture-et-plan-initial.md   # plan de départ (partiellement dépassé, voir §13)
│   ├── 02-phase0-exemples-et-syntaxe.md     # les 20 exemples et décisions de syntaxe
│   └── 03-plan-site-et-application-android.md
├── pom.xml                      # agrégateur Maven racine (compiler + playground)
├── scripts/
│   ├── build.sh                 # compile sans Maven → build/dystos.jar, build/dystos-playground.jar
│   ├── dystos                   # lanceur : run | test | build | jrun
│   ├── build.ps1  dystos.ps1  run-examples.ps1  find-jdk.ps1   # équivalents Windows natif
│   ├── playground               # lance le service HTTP
│   ├── run-examples.sh          # exécute les 20 exemples
│   ├── compare-backends.sh      # interpréteur contre bytecode sur tous les exemples
│   ├── bench.sh                 # performances : interpréteur contre bytecode (benchmarks/)
│   ├── generate-grammar.sh      # régénère les grammaires (editors/)
│   ├── test-editors.sh          # vérifie les grammaires (Node.js)
│   └── ci.sh                    # CI locale : mvn test + exemples + grammaires
├── tools/GenerateGrammar.java   # générateur des grammaires (source : Highlighter)
├── editors/                     # grammaires TextMate / Prism, extension VS Code, tests, démo
├── services/playground/         # service HTTP (analyze, run), Dockerfile, deploy/
└── compiler/
    ├── pom.xml                  # agrégateur Maven (lexer, parser, sema, interp)
    ├── lexer/    # jetons, indentation
    ├── parser/   # AST et analyse syntaxique
    ├── sema/     # analyse sémantique
    ├── interp/   # interpréteur, sandbox, CLI
    ├── runtime/  # bibliothèque d'exécution des programmes compilés (dystos.rt)
    └── jvm/      # compilateur vers la JVM : bytecode, .jar
        (chaque module : pom.xml, README.md, src/main, src/test, src/test/resources/exemples)
```

Les 21 fichiers `.dys` des exemples sont recopiés dans chaque module (ils servent de tests). Si tu modifies un exemple, modifie-le partout (voir §8, recette « ajouter un exemple »).

---

## 4. Installer et vérifier sur ta machine

### 4.1 Prérequis

- **JDK 21** (`java -version` doit afficher 21 ou plus ; `javac -version` aussi).
- **Maven 3.9+** (`mvn -v`) — facultatif si tu utilises `scripts/build.sh`.
- **Git**.
- Sous Windows : deux options. **Natif** (recommandé pour démarrer) : PowerShell 7+ et un JDK 21 ; utiliser `scripts\build.ps1`, `scripts\dystos.ps1`, `scripts\run-examples.ps1` (voir §4.5). **WSL2** : place alors le projet **dans le système de fichiers Linux** (par exemple `~/projets/Dystos`), pas sous `/mnt/c/...` : c'est bien plus rapide.

### 4.2 Étapes

```bash
# 1. Décompresser dans le dossier Linux de WSL2
mkdir -p ~/projets && cd ~/projets
unzip /mnt/c/Users/<toi>/Downloads/Dystos-projet-complet.zip
cd Dystos

# 2. Vérifier avec Maven (la référence), depuis la racine
mvn test
#    attendu : 7 modules, 281 tests, 0 échec

# 3. Ou compiler sans Maven
scripts/build.sh                       # produit build/dystos.jar

# 4. Exécuter
scripts/dystos run compiler/interp/src/test/resources/exemples/20-programme-complet-gestion-de-stock.dys
scripts/run-examples.sh
scripts/dystos build compiler/interp/src/test/resources/exemples/20-programme-complet-gestion-de-stock.dys -o stock.jar
java -jar stock.jar                              # bytecode JVM autonome
scripts/compare-backends.sh                      # interpréteur contre bytecode

# 4 bis. Playground et grammaires
scripts/playground --port 8080 &                 # puis : curl -s localhost:8080/api/v1/health
scripts/test-editors.sh                          # Node.js 18+ requis

# 5. Mettre sous Git
git init && git add . && git commit -m "Dystos : lexer, parser, sémantique, interpréteur"
git branch -M main
git remote add origin https://github.com/Lunkht/Dystos.git
git push -u origin main
```

### 4.3 Sorties attendues des exemples

| Exemple | Sortie attendue |
|---|---|
| 01 | `Bonjour le monde` |
| 02 | `Lunkht a 31 ans, 1.75 m, actif=true` |
| 05 | `pomme 3` · `pomme: 3` · `poire: 5` · `kiwi: 7` · `3` |
| 06 | `[0, 4, 16, 36, 64] {"a": 1, "bcd": 3, "ef": 2} {0, 2, 4}` |
| 08 | `Compte(Awa, 150.0)` |
| 09 | `13.0` |
| 12 | `Erreur : Il manque 15.0` puis `Fin` |
| 13 | `[2, 4, 6] [2, 4, 6] ["a", "bb", "ccc"]` |
| 17 | `[0, 1, 4, 9]` |
| 20 | `riz` · `introuvable` · `Valeur totale : 278.00` |
| 11 | refusé : `E0201` (`null` pour un `str`), code de sortie 2 |
| 18 (a+b) | refusé : `E0104` (`_secret` est privé), code de sortie 2 |

Les sorties complètes de référence sont dans `compiler/interp/src/test/java/.../RunnerTest.java`.

> `scripts/run-examples.sh` exécute les exemples en mode `--sandbox` : l'exemple 15 (écriture de fichier) y est refusé avec le code 1, et c'est normal ; `RunnerTest` l'exécute avec les fichiers autorisés dans un dossier temporaire. Pour le lancer à la main : `scripts/dystos run <fichier>` (mode développeur, fichiers dans le dossier courant).

### 4.4 Problèmes courants

| Symptôme | Cause probable | Remède |
|---|---|---|
| `release version 21 not supported` | JDK trop ancien | installer JDK 21 |
| Accents cassés à l'affichage | encodage | `java -Dfile.encoding=UTF-8 ...` (le script `dystos` le fait déjà) |
| Tests qui ne trouvent pas `src/test/resources/exemples` | mauvais dossier courant | lancer `mvn test` depuis la racine (Maven se place dans chaque module) |
| `PlaygroundTest` : mode « processus » en échec | `java` introuvable ou mémoire insuffisante pour la JVM fille | vérifier `java.home` ; fermer d'autres programmes ; le test limite la fille à 48-96 Mo |
| `ClassNotFoundException` au lancement manuel | classpath incomplet | utiliser `build/dystos.jar` (contient les 4 modules) |
| Build très lent | projet sous `/mnt/c` | déplacer dans `~/projets` |
| Fins de ligne `^M` dans les scripts | fichiers modifiés sous Windows | `.gitattributes` force LF ; sinon `dos2unix scripts/*` |
| `UnsupportedClassVersionError` (class file version 65.0) | le `java` du `PATH` est un JRE 8 | utiliser `scripts\*.ps1` (ils prennent le JDK 21) ou pointer `JAVA_HOME` vers le JDK 21 |

### 4.5 Sous Windows natif (PowerShell)

Trois scripts remplacent les scripts Bash, sans WSL2 :

```powershell
scripts\build.ps1                                                   # → build\dystos.jar + build\dystos-playground.jar
scripts\dystos.ps1 run   compiler\interp\src\test\resources\exemples\01-hello-world.dys
scripts\dystos.ps1 test  compiler\interp\src\test\resources\exemples\19-tests-integres.dys
scripts\dystos.ps1 build compiler\interp\src\test\resources\exemples\08-classes-et-constructeurs.dys -o compte.jar
scripts\dystos.ps1 jrun  compiler\interp\src\test\resources\exemples\01-hello-world.dys
scripts\run-examples.ps1                                            # les 20 exemples et leurs codes de sortie
mvn test                                                            # 281 tests (si Maven est installé)
```

- `scripts\find-jdk.ps1` localise le JDK : `JAVA_HOME`, puis installations Eclipse Adoptium / Oracle, puis `javac`/`java` du `PATH`. Si `JAVA_HOME` pointe vers un dossier absent, il est ignoré.
- Si `java` du `PATH` est un JRE 8 (cas fréquent quand Oracle installe un raccourci système), les scripts prennent quand même le `java.exe` du JDK 21 : sinon la JVM refuse les classes en version 65 (`UnsupportedClassVersionError`).
- `scripts\dystos.ps1` ne déclare aucun paramètre nommé : `-o`, `--sandbox`, `--json` sont transmis tels quels (sans quoi PowerShell interpréterait `-o` comme un préfixe de `-OutVariable`).
- Le mode « processus fils » du playground fonctionne : `PlaygroundTest` passe aussi sous Windows.
- Les scripts `.sh` restent la référence sous Linux/WSL2 ; les deux jeux produisent les mêmes `build\dystos.jar`.

---

## 5. Chronologie de ce qui a été fait

1. **Cadrage.** Demande d'un langage mêlant Python et Java. Production d'un plan d'architecture en 7 phases (`docs/01`). Le nom provisoire « Pyva » a été refusé ; le nom retenu est **Dystos** (troncation de « dystopiques »). Dépôt GitHub créé après vérification que le nom était libre.
2. **Phase 0.** Rédaction des 20 programmes d'exemple (`docs/02`). Décisions : `self` **explicite** (style Python) ; `val`/`var` conservés ; `null` sur un type non nul et accès à un membre privé sont des **erreurs** ; `print(x, y)` sépare par un espace ; extension `.dys`.
3. **Phase 1 — lexer.** Indentation par pile (INDENT/DEDENT), refus des tabulations, f-strings conservées brutes, continuation par `\`, lignes vides et commentaires ignorés. Ambiguïté résolue : `-> Produit?:` donne `?` puis `:` (le `?:` n'est un opérateur « elvis » que si le `:` ne termine pas la ligne).
4. **Phase 1 — parser.** Descente récursive, AST en `record` avec interfaces scellées, précédence complète, génériques, types fonction, `match`, `try`, `with`, décorateurs, compréhensions, f-strings découpées. **Scripts autorisés** : les instructions au niveau fichier sont rangées dans `SourceFile.statements` (un `main` implicite à la compilation) ; fichier avec instructions **et** `main` = erreur.
5. **Plan du site et de l'application Android** (`docs/03`) : Docusaurus, application Java en Clean Architecture, contrats d'API, jalons J0 à J6. Développement confié à toi, en parallèle.
6. **Phase 2 — sémantique.** Table des symboles, inférence locale, génériques avec unification, lambdas typées par le contexte, null-safety, immutabilité, vérification des classes/interfaces/redéfinitions, interopérabilité Java par réflexion (sans exécuter l'initialisation des classes), 33 codes de diagnostic, JSON. Deux bugs trouvés par les tests et corrigés (liaison des modules importés, contrôle de visibilité des membres de module).
7. **Interpréteur d'AST.** Exécution complète des exemples, exceptions, classes, enums, `match`, lambdas, f-strings avec formats, modules, `io` et `concurrent`, interop Java, **limites** (instructions, durée, sortie, profondeur), **sandbox** (classes Java interdites, fichiers désactivés), CLI `run`/`test`. Un bug de formatage de flottants corrigé.
8. **Consolidation.** Tests rejoués avec le vrai JUnit (164 réussis), compatibilité Java 17 vérifiée, scripts de build sans Maven, ce guide.

9. **Étape 2 : coloration et playground.**
   - `Highlighter` (module `lexer`) : colorateur **tolérant** (code incomplet, commentaires conservés), indices UTF-16 ; source unique des listes de mots-clés, types, fonctions prédéfinies et opérateurs.
   - `tools/GenerateGrammar.java` : génère la grammaire **TextMate**, la définition **Prism** et une **extension VS Code** ; il refuse de générer si un mot-clé n'est pas classé (anti-oubli).
   - Vérification avec les vrais moteurs. Trouvés et corrigés grâce à elle : un `/` non échappé dans la regex Prism, et l'ordre des règles de l'interpolation Prism.
   - **Service playground** : `analyze` (diagnostics, jetons, AST, coloration) et `run` ; deux modes d'exécution (`inprocess`, `process` avec JVM fille jetable) ; limites de taille, de débit et de concurrence ; CORS et proxy configurables.
   - Corrections annexes : classpath de la JVM fille déduit des classes (le lanceur JUnit le cachait), statut HTTP absent du journal, gestion du manque de mémoire dans `Runner`, CLI avec lecture sur stdin et limites en option.

10. **Phase 3a : backend JVM.**
   - `TypedProgram` : la sémantique exporte désormais types, symboles résolus, appels (surcharge choisie, types génériques inférés), membres et variables déclarées.
   - Écrivain de fichiers `.class` maison (version 49, sans dépendance) vérifié par la JVM, y compris le rejet d'un bytecode invalide.
   - Bibliothèque d'exécution `dystos.rt` embarquée dans chaque `.jar`.
   - Générateur de code : fonctions, classes, interfaces, énumérations, exceptions, `try/finally` avec duplication du code de nettoyage, `match`, compréhensions, f-strings, null-safety, génériques par effacement.
   - **Méthode de validation : tests différentiels** (même programme exécuté par l'interpréteur et par le bytecode). Ils ont révélé trois divergences, corrigées : affichage de `Entry`, nom de `Exception` dans l'erreur non rattrapée, guillemets dans les messages d'erreur.
   - Règle de typage tranchée : `int ** int` donne un `int`, un exposant négatif est une erreur.
   - Un défaut du compilateur trouvé par les exemples (plantage au lieu d'un diagnostic après une construction non supportée) a été corrigé.
   - **Incident de parcours** : l'environnement de travail a été réinitialisé en cours de route (dossier de travail et JDK perdus). Le travail a été reconstruit depuis l'archive précédente puis revérifié intégralement ; les instantanés sont désormais sauvegardés régulièrement.

11. **Phase 3b : le backend couvre tout le langage.**
   - **Lambdas et fermetures** : classes synthétiques qui implémentent `dystos.rt.Fn` (pas besoin d'`invokedynamic`) ; une variable capturée **et réaffectée** vit dans une boîte partagée `Ref`, les autres sont copiées. La sémantique calcule les captures.
   - **`io` et `concurrent`** : fichiers confinés au répertoire courant (mêmes règles que l'interpréteur) ; **vrais threads** pour les tâches (le commentaire de l'interpréteur le prévoyait).
   - **Interopérabilité Java** : appel direct avec le descripteur exact de la méthode réelle, conversions aux frontières (`int` ↔ `long`/`int`/`short`, `char` ↔ `str`, types encadrés). Deux défauts de la sémantique trouvés et corrigés : surcharges Java de même signature Dystos choisies au hasard, et ordre des surcharges.
   - **Globales de script** (champs statiques), **méthodes pont** (génériques), **méthodes par défaut et statiques d'interface** (classe compagnon `$Defaults`), **numéros de ligne** des erreurs (`LineNumberTable`).
   - **Défaut du vérificateur** trouvé en route : un objet non initialisé ne doit pas être sur la pile pendant une boucle (version 49) ; les arguments à boucle d'un constructeur sont évalués avant le `new`.
   - **Défaut du service playground** trouvé grâce à un test intermittent : le créneau de concurrence était libéré *après* l'envoi de la réponse, donc un client qui relançait aussitôt pouvait recevoir `429` à tort. Corrigé (libération avant l'envoi).
   - **Mesures de performance** (`scripts/bench.sh`) : le bytecode est de l'ordre de 30 à 95× plus rapide sur le calcul, ≈ 2,5× sur des listes d'entiers encadrés.
   - Résultat : 61 tests différentiels, les 20 exemples compilent.

*Incident de parcours :* des fichiers d'une ébauche antérieure du module `sema` (conception différente, jamais livrée) traînaient dans mon dossier de travail ; ils ont été mis de côté et ne font pas partie de l'archive.

---

## 6. Décisions de conception

### Syntaxe
| Sujet | Décision |
|---|---|
| Blocs | indentation (espaces uniquement ; tabulation = erreur) |
| Instance courante | `self`, premier paramètre explicite de chaque méthode d'instance |
| Constructeur | `def init(self, ...)` ; une sous-classe sans `init` hérite de celui de sa superclasse |
| Champs | déclarés dans le corps de la classe (`nom: str`), accessibles par `self.nom` seulement |
| Variables | `val` (immuable), `var` ou `x = 5` (mutable), `x: T = v` |
| Nullable | `T?`, accès sûr `?.`, valeur par défaut `?:` |
| Visibilité | public par défaut ; préfixe `_` = privé (classe ou module) |
| Lambdas | `x -> x + 1`, `(a, b) -> a + b` |
| `{}` | map vide ; `{1, 2}` est un set |
| Scripts | instructions de niveau fichier autorisées (pas avec un `main`) |
| Décorateurs connus | `@static`, `@test` |

### Typage
| Sujet | Décision |
|---|---|
| Conversions | `int` → `float` implicite ; l'inverse est refusé |
| Division | `/` donne **toujours** un `float` (`4 / 2` = `2.0`) ; `%` suit Python (signe du diviseur) ; `int ** int` donne un `int` (exposant négatif = erreur, utiliser `2.0 ** -1`) |
| Texte | `+` n'assemble que des `str` ; pas de conversion implicite (utiliser une f-string) |
| Conditions | type `bool` obligatoire (pas de « vrai/faux » implicite) |
| Déclaration par affectation | `x = 5` déclare si `x` n'existe pas dans la portée, sinon réaffecte |
| Portée | par bloc (comme Java) ; paramètres et `val` immuables |
| Surcharge | interdite pour les fonctions et méthodes Dystos ; permise pour les classes Java |
| Inférence | locale ; types obligatoires sur les signatures (paramètres) ; sans `->`, une fonction ne retourne rien |
| Collections vides | `[]` et `{}` exigent une annotation |

### Exécution et sécurité
| Sujet | Décision |
|---|---|
| Valeurs | `int`=long (débordement = erreur), `float`=double, `str`=String, `list`=ArrayList, `map`=LinkedHashMap, `set`=LinkedHashSet |
| Erreurs d'exécution | `RuntimeError` (indice, clé, division par zéro, débordement) ; `ValueError` (conversions) |
| Java accessible | liste de préfixes autorisés + liste de refus ; le sandbox exclut `java.io`, `java.net`, `java.nio`, `Runtime`, `System`, `Thread`, `java.util.concurrent`... |
| Concurrence | `concurrent.run` s'exécute séquentiellement pour l'instant |

### Service playground
| Sujet | Décision |
|---|---|
| Technologie | serveur HTTP du JDK, JSON maison : aucune dépendance |
| Écoute | `127.0.0.1` par défaut ; un proxy inverse doit se placer devant |
| Limites | corps ≤ 20 000 octets ; 20 exécutions et 120 analyses par minute et par client ; 4 exécutions simultanées |
| Isolation | mode `process` recommandé : JVM fille jetable, environnement vide, mémoire plafonnée, arrêt forcé |
| Java accessible | mêmes listes que le sandbox, appliquées aussi à `analyze` |
| Authentification | aucune (à ajouter ou à remplacer par une restriction réseau) |

### Backend JVM
| Sujet | Décision |
|---|---|
| Générateur | écrivain de `.class` maison, **sans ASM** (souveraineté, aucune dépendance) |
| Format de classe | **version 49** : pas de `StackMapTable`, vérification par inférence ; pas d'`invokedynamic` ni de méthodes par défaut d'interface (à revoir pour le palier 3b) |
| Types | `int`=long, `float`=double, `bool`=boolean, `str`=String ; génériques par **effacement** ; `T?` primitif = classe d'encadrement |
| Exécution | runtime `dystos.rt` **embarqué** dans le `.jar` (Java 17 minimum) |
| Non supporté | refusé par le diagnostic `E0401` avec position ; **jamais de bytecode incorrect** |
| Lambdas | classes synthétiques `Fn` ; variables capturées copiées, ou boîte `Ref` si réaffectées ; **la version de classe reste 49** |
| Interfaces | méthodes par défaut et statiques dans une classe compagnon `Interface$Defaults` + redirections (comme les `DefaultImpls` de Kotlin) |
| Concurrence | un vrai thread par tâche (l'interpréteur exécute en séquence) |
| Fichiers | confinés au répertoire courant, comme l'interpréteur |
| Null Java | un `null` Java utilisé comme `int`/`float`/`bool` est une `RuntimeError` (null-safety) |
| Validation | tests différentiels contre l'interpréteur ; en cas d'écart, le bytecode fait foi quand il applique les types statiques (`int → float`) |

---

## 7. Architecture du code

```
 source .dys
    │  Lexer.tokenize            (module lexer)     → List<Token>
    ▼
 Parser.parse                    (module parser)    → Ast.SourceFile
    ▼
 SemanticAnalyzer.analyze        (module sema)      → List<Diagnostic>   (erreurs = on s'arrête)
    ▼
 Interpreter / Runner.run        (module interp)    → stdout, stderr, exitCode
```

### Modules et classes principales

**`lexer`** (package `com.vulsoft.dystos.lexer`)
- `Lexer` : analyse en une passe ; pile d'indentation ; délimiteurs ouverts suivis pour ignorer les retours à la ligne.
- `TokenType`, `Token(type, text, line, column)`, `LexerException`.
- `Highlighter` : colorateur tolérant (spans d'indices UTF-16) ; sert l'application Android et le playground, et alimente les grammaires générées.

**`parser`** (packages `...ast`, `...parser`)
- `Ast` : tous les nœuds (records). `Expr`, `Stmt`, `TypeRef`, `TopLevel`, `Member` sont scellés.
- `Parser` : descente récursive. Les f-strings sont redécoupées en `FText`/`FExpr` en relançant le lexer sur chaque `{expr}`.
- `AstPrinter`, `AstDump` : affichage de l'arbre (débogage).

**`sema`** (package `...sema`)
- `SemanticAnalyzer` : API publique (`check`, `checkAll`, `analyze`).
- `Analyzer` : orchestration en phases : modules → imports → déclarations → en-têtes de classes → cycles → membres et signatures → inférence des champs → vérifications de classes → corps.
- `Checker` : typage des corps (le plus gros fichier). Appels : `resolve` choisit la surcharge puis `apply` infère les paramètres de type (arguments non-lambdas d'abord, lambdas ensuite).
- `Types` : `isAssignable`, `unify`, `substitute`, `join`, `linearize`. `Type` : `ClassType`, `FunctionType`, `TypeVar`, `NullType`, `UnitType`, `ErrorType` (ce dernier évite les erreurs en cascade).
- `Symbols`, `Scope`, `Builtins` (types et fonctions prédéfinis), `JavaInterop` (réflexion), `Ctx` (diagnostics et résolution de noms), `Suggest` (« vouliez-vous dire »), `Diagnostic`.

**`interp`** (package `...interp`)
- `Runner` : API publique (`run`, `runModules`, `runTests`), options, résultat JSON, fil d'exécution avec grande pile.
- `Interpreter` : évalue instructions et expressions ; signaux de contrôle (`ReturnSignal`, `BreakSignal`...) ; `DystosThrow` pour les exceptions Dystos ; `LimitExceeded` pour les limites (non rattrapable).
- `Natives` : fonctions prédéfinies, méthodes de `str`/`list`/`map`/`set`, modules `io` et `concurrent`.
- `JavaBridge` : appels Java par réflexion, choix de la surcharge par « coût » de conversion, contrôle des classes autorisées.
- `Formatter` : formats d'f-strings. `Values`, `Env` : valeurs et environnements. `Main` : CLI.

**`playground`** (package `...playground`, dossier `services/playground`)
- `Playground` : serveur, routage, validation des requêtes, limites. `Json` : lecteur/écrivain JSON. `RateLimiter` : fenêtre glissante par client.
- `RunService` : exécution `inProcess` ou `childProcess` (JVM fille).

**`tools` et `editors`** : `GenerateGrammar` produit les grammaires à partir de `Highlighter` ; `editors/tests/verify.js` les valide avec Node.

**`runtime`** (package `dystos.rt`, Java 17) : `Rt` (appels du code généré), `Fmt`, `DObject`, `DException` & co, `DEnum`, `Entry`, `Fn` (réservé aux lambdas).

**`jvm`** : `Compiler` (API et `.jar`), `ModuleGen`, `ClassGen`, `MethodGen` (instructions et expressions, le gros du travail), `Jt` (types → descripteurs), `RuntimeMap` (fonctions prédéfinies → `Rt`, vérifiées à la compilation), `classfile/` (`ClassWriter`, `Code`, `ConstPool`, `Label`), `BuildMain`. Voir `compiler/jvm/README.md`.

### Principe important
Le **vérificateur de types (`sema`)** raisonne sur les types ; l'**interpréteur** est dynamique ; le **backend JVM** utilise les types
calculés par `sema` (`TypedProgram`). Il y a donc **deux implémentations de l'exécution** (interpréteur et `dystos.rt`) : toute règle
nouvelle doit être ajoutée aux deux, et les tests différentiels (`JvmTest`, `scripts/compare-backends.sh`) détectent les écarts.
À terme, l'interpréteur pourrait être réduit à un mode de développement (REPL) pendant que le bytecode devient la voie principale.

---

## 8. Recettes : modifier le langage

Chaque recette se termine par : *ajouter un test dans le module concerné, puis relancer `mvn test`*.

### Ajouter un mot-clé ou un opérateur
1. `lexer/TokenType.java` : ajouter le jeton (les mots-clés sont contigus de `DEF` à `IS` : le parser s'en sert pour les autoriser après un `.`).
2. `lexer/Lexer.java` : table `KEYWORDS`, ou méthode `operator`.
3. `parser/Ast.java` : nouveau nœud si nécessaire ; `parser/Parser.java` : règle de grammaire (respecter la précédence documentée dans l'en-tête).
4. `sema/Checker.java` : typage ; `sema/Analyzer.java` si c'est une déclaration.
5. `interp/Interpreter.java` : évaluation.
6. Mettre à jour `docs/02-phase0-exemples-et-syntaxe.md` et la grammaire de coloration.

### Ajouter une fonction prédéfinie (ex. `round`)
1. `sema/Builtins.java` : `function("round", INT_T, FLOAT_T)` (une ligne par surcharge).
2. `interp/Interpreter.java` : ajouter son nom à la liste du constructeur.
3. `interp/Natives.builtin` : un `case "round"`.
4. Test d'exécution dans `RunnerTest`.

### Ajouter une méthode à `str`, `list`, `map` ou `set`
1. `sema/Builtins.java` : `method(STR, "capitalize", STR_T)`.
2. `interp/Natives.java` : `stringMethod` / `listMethod` / `mapMethod` / `setMethod`.

### Ajouter un module de bibliothèque standard (ex. `math`)
1. `sema/Builtins.java` : créer un `ModuleSymbol` et l'enregistrer dans `MODULES` (comme `io`).
2. `interp/Natives.java` : une méthode `mathModule(...)` renvoyant une `Map<String,Object>` de `NativeFn`.
3. `interp/Interpreter.bindImports` : brancher le nom du module.

### Ajouter un code de diagnostic
Utiliser `ctx.error("E04xx", "message", pos)` dans `Checker`/`Analyzer`, documenter le code dans `compiler/sema/README.md`, tester dans `SemaTest`.

### Ajouter un exemple
Créer le `.dys`, l'ajouter dans les 4 dossiers `src/test/resources/exemples` (ou factoriser, voir backlog), ajouter la sortie attendue dans `RunnerTest`.

---

## 9. Feuille de route pas à pas

Chaque étape donne : objectif, étapes, fichiers, critère de réussite. Les durées supposent du temps partiel.

### Étape 0 — Reprise (1 jour)
1. Suivre le §4 : `mvn test` doit donner 281 tests verts. **Si un test échoue sous Maven, corrige-le avant toute autre chose.**
2. `git init`, premier commit, `git push` vers le dépôt.
3. Créer une intégration continue locale simple : un script `scripts/ci.sh` qui lance `mvn test` puis `scripts/run-examples.sh`.
4. *Critère* : dépôt poussé, CI locale verte.

### Étape 1 — Consolider le langage (1 à 2 semaines)
Chantiers par ordre de valeur :

**1a. Smart cast** (`if x != null:` rend `x` non nul dans le bloc). C'est la limite la plus gênante en pratique.
1. Dans `Checker`, ajouter à `Scope` une table « types raffinés » : `Map<String,Type> narrowed`.
2. Dans `stmt(IfStmt)`, analyser `cond` : motifs `x != null`, `x == null` (branche else), `x is not null`, `a and b`, `not`.
3. Évaluer le `then` dans une portée fille où `x` a le type `nonNull`; idem `else` pour `x == null`.
4. Invalider le raffinement après une affectation à `x` dans le bloc.
5. Tests : accès `.` accepté dans le bloc, refusé en dehors.
*Fichiers* : `sema/Scope.java`, `sema/Checker.java`. L'interpréteur n'est pas concerné.

**1b. Fonctions locales** (code `E0399` aujourd'hui).
1. `Analyzer.makeFunc` est déjà réutilisable : l'appeler depuis `Checker.stmt(FuncDecl)` avec la portée courante.
2. Déclarer un `FuncGroup` dans la portée locale ; vérifier le corps comme une fermeture.
3. `Interpreter.exec` : `env.define(name, new FuncValue(decl, env, null), false)`.

**1c. Division entière et `input()`** : décider de la syntaxe (`//` n'est pas tokenisé : `/` puis `/`). Ajouter `SLASHSLASH` au lexer, `BinaryOp.FLOORDIV`, typage `int//int → int`.

**1d. Tuples et affectations multiples** (`a, b = b, a`) : demande un nouveau type (`tuple[T, U]`) dans `Type`, `Ast`, `Checker`, `Interpreter`. À faire après la Phase 3 si possible.

**1e. Dédoublonner les exemples** : un seul dossier `examples/` à la racine, référencé par les tests (propriété Maven `exemples.dir`).

*Critère* : `docs/02` mis à jour, tests ajoutés, 281 + N tests verts.

### Étape 2 — Débloquer le site et l'application ✅ faite
**Livré** : colorateur `Highlighter`, grammaires TextMate et Prism générées, extension VS Code, service playground (`analyze`, `run`, `health`, `limits`), mode d'exécution isolé, tests et documentation (`editors/README.md`, `services/playground/README.md`).

**Reste à faire de ton côté (rapide)**
1. **Valider Docker** : `cd services/playground/deploy && docker compose up -d --build`, puis `curl` sur le port 8081. Ajuster si besoin (versions d'images, droits). Recopier `eclipse-temurin` et `caddy` dans un registre à toi pour rester souverain.
2. **Brancher le site** : suivre `editors/README.md` (Prism dans Docusaurus) et l'exemple `fetch` de `services/playground/README.md`.
3. **Brancher l'application Android** : utiliser `Highlighter` pour la coloration et les diagnostics de `SemanticAnalyzer` pour les erreurs ; tester la compatibilité Android (voir ci-dessous).
4. **Décider de l'accès** : le service n'a pas d'authentification ; soit un jeton d'API simple (en-tête `Authorization`, à ajouter dans `Playground.route`), soit une restriction réseau.
5. **Mesurer** le coût du mode `process` (environ 0,2 à 0,5 s de démarrage par exécution) et décider d'un pool de workers préchauffés si nécessaire.

### Étape 3 — Phase 3 : backend JVM — ✅ palier 3a et palier 3b faits
**Livré** : `TypedProgram` (3.1), écrivain de classes sans dépendance (3.2), correspondance Dystos → JVM (3.3), génération du
langage complet (3.4), **tests différentiels** (3.5), `dystos build` / `jrun` et `.jar` autonome (3.6), puis le palier 3b :
lambdas et fermetures, `io`, `concurrent`, interopérabilité Java, variables globales de script, méthodes pont, méthodes par
défaut d'interface, numéros de ligne. Détail et choix de conception dans `compiler/jvm/README.md`.

**Suite possible (rien de bloquant)**, par ordre de valeur :
1. **Java : tableaux, arguments variables, interfaces fonctionnelles.** `String.format("%d", n)`, `list.toArray`, passer une lambda Dystos à une méthode Java qui attend `Runnable`/`Comparator` (classe synthétique qui implémente l'interface Java demandée), hériter d'une classe Java.
2. **Version de classe ≥ 52** : permettrait d'appeler les méthodes statiques d'interface Java (`List.of`, `Map.of`) et d'utiliser `invokedynamic`. Exige de calculer la `StackMapTable` : soit écrire ce calcul (le suivi de pile de `Code` est une bonne base), soit copier ASM (`COMPUTE_FRAMES`) en local pour rester hors ligne. Plus aucune fonctionnalité du langage ne l'exige.
3. **`@test` compilé** : un lanceur de tests dans le `.jar` (aujourd'hui : `scripts/dystos test`, interpréteur).
4. **Fonctions locales** (fonction déclarée dans une fonction) et appel d'un champ de type fonction (`self.callback(x)`).
5. **Performances** : les premières mesures sont faites (`scripts/bench.sh`, voir `compiler/jvm/README.md`) ; la piste restante est de typer les listes de primitives sans encadrement (c'est elle qui limite le gain à ≈ 2,5× sur `listes`).
6. **Options de compilation** : version minimale de Java visée, point d'entrée, mode « sans limites » ou « bac à sable » pour le playground.
7. **Playground sur le bytecode** : aujourd'hui le service exécute l'interpréteur sandboxé ; le bytecode n'a pas de limites de ressources, il faudrait un processus fils avec `-Xmx`, délai et pas de réseau.

*Critère atteint* : les 20 exemples compilent et donnent la même sortie que l'interpréteur (`scripts/compare-backends.sh`).

### Étape 4 — Phase 4 : bibliothèque standard (4 semaines)
1. Inventaire : `math` (abs, round, sqrt...), `text`/méthodes de `str` (capitalize, find, format...), `time` (sur `java.time`), `json`, `fs` (fichiers), `collections` (tri, min/max, zip, map/reduce), `random`.
2. Écrire autant que possible la stdlib **en Dystos** (`stdlib/*.dys` embarqué dans le jar) et seulement le reste en natif.
3. Conversions entre `list`/`map` Dystos et `java.util.List`/`Map` (limite actuelle de l'interop) : décider si ce sont les mêmes types (c'est le cas à l'exécution) et l'exprimer dans `JavaInterop` (faire de `java.util.List` un super-type de `list`).
4. Documenter chaque fonction (signature, exemple) : ces pages alimentent le site.

### Étape 5 — Phase 5 : outillage (5 semaines)
1. **`dystos` unifié** (`run`, `check`, `test`, `fmt`, `repl`, `build`, `ast`) : un module `cli` ; empaquetage du jar avec toutes les dépendances ; script `dystos` fourni.
2. **REPL** : garder un `Env` et un contexte sémantique persistants ; analyser chaque entrée comme un script incrémental.
3. **Formateur `dystos fmt`** : le lexer **supprime les commentaires** : ajouter des « trivia » (commentaires, lignes vides) aux jetons avant de pouvoir reformater sans les perdre.
4. **Serveur LSP** : diagnostics (déjà prêts, JSON), complétion et survol à partir des tables de symboles, aller à la définition (positions des symboles à conserver).
5. **Gestionnaire de paquets** : fichier `dystos.toml` (nom, version, dépendances, point d'entrée), dépôt local ou autohébergé.
6. **Extension VS Code** : coloration (étape 2a) + LSP.

### Étape 6 — Phase 6 : stabilisation (4 semaines)
1. Fuzzing du lexer et du parser (entrées aléatoires : aucun plantage, seulement des diagnostics).
2. Benchmarks (boucles, appels, collections) et profilage ; optimisations de l'interpréteur et du bytecode.
3. Revue des messages d'erreur (positions de fin, suggestions), documentation utilisateur complète.
4. Checklist 1.0-bêta : tous les tests verts, exemples compilés et interprétés identiques, documentation à jour, version signée.

### Piste parallèle — site et application (voir `docs/03`)
| Ce que tu construis | Dépend de |
|---|---|
| Squelette du site, thème, contenu `content/` | rien |
| Éditeur du playground, diagnostics soulignés | étape 2b |
| Coloration | étape 2a |
| Application : parcours hors ligne | rien |
| Application : vérification locale du code | modules `lexer`, `parser`, `sema` (vérifier la compatibilité Android) |
| Application : exécution locale | module `interp` (vérifier la compatibilité Android) |

**Compatibilité Android** : les modules sont écrits sans fonctionnalités Java 21 spécifiques (compilation vérifiée en Java 17 : records, interfaces scellées, filtrage de motifs d'`instanceof`). Reste à tester : desugaring des APIs récentes selon ton `minSdk`, et la réflexion de `JavaBridge`. Pour une application hors ligne, désactiver l'interop Java (`javaPrefixes` vide) est le plus simple.

---

## 10. Contrats d'interface

**Diagnostic**
```json
{"severity":"error","code":"E0201","message":"'null' ne peut pas être utilisé comme 'str' (utilisez 'str?')","line":6,"column":14}
```
(`"file"` ajouté lorsque plusieurs fichiers sont analysés ; `severity` vaut `error` ou `warning`.)

**Résultat d'exécution** (`Runner.Result.toJson()`)
```json
{"stdout":"13.0\n","stderr":"","exitCode":0,"durationMs":12,"diagnostics":[]}
```
Codes de sortie : 0 succès · 1 exception non rattrapée · 2 erreurs de compilation · 3 limite dépassée · 4 erreur interne.

**API du playground** : `services/playground/README.md` (routes, schémas, statuts HTTP, sécurité).

**Codes de diagnostic** : tableau complet dans `compiler/sema/README.md` (E0001-E0002 syntaxe, E01xx noms, E02xx types, E03xx classes, W02xx avertissements).

---

## 11. Limites connues et backlog

| Limite | Effet | Où la traiter |
|---|---|---|
| Pas de smart cast | `if x != null:` ne suffit pas, il faut `?.` ou `?:` | étape 1a |
| Pas de fonctions locales | erreur `E0399` | étape 1b |
| Pas de tuples ni d'affectation multiple | `a, b = ...` impossible | étape 1d |
| Pas de division entière `//` ni de `input()` | | étape 1c |
| `int→float` non appliqué dans les collections | `[1, 2.5]` garde un `1` entier à l'exécution | étape 3.1 |
| Pas de surcharge de méthodes Dystos | erreur `E0102` | à décider |
| Héritage d'une classe Java | refusé à l'exécution | étape 3 |
| `concurrent.run` séquentiel | pas de vrai parallélisme | étape 3.4 |
| Interop `list` ↔ `java.util.List` | à clarifier | étape 4 |
| Commentaires perdus par le lexer | bloque `fmt` | étape 5.3 |
| Pas de positions de fin dans les diagnostics | soulignement approximatif dans l'éditeur | étape 6 |
| Exemples dupliqués dans 4 modules | risque de divergence | étape 1e |
| Fil d'exécution non interruptible dans un appel Java bloquant | limite du sandbox en mode `inprocess` | utiliser le mode `process` et un conteneur |
| Playground sans authentification, limites de débit en mémoire (par instance) | à durcir avant une exposition publique | étape 2, point 4 |
| Backend JVM : tableaux Java, `String.format` (arguments variables), interfaces fonctionnelles Java, héritage de classe Java, méthodes statiques d'interface Java, fonctions locales | refus `E0401` avec position | étape 3, « suite possible » |
| Backend JVM : récursion infinie non rattrapable, pas de limites de ressources (temps, mémoire, sortie), tâches `concurrent` en vrai parallèle | un programme compilé est un programme Java ordinaire | le playground reste sur l'interpréteur sandboxé |
| Deux implémentations de l'exécution (interpréteur et `Rt`) | risque de divergence | tests différentiels ; à terme, réduire l'interpréteur à un mode de développement |
| Grammaires approchées pour certains cas rares (ex. `:` de format dans une expression avec crochets complexes) | coloration imparfaite, sans conséquence sur le langage | `Highlighter` et `GenerateGrammar` |

---

## 12. Reprendre avec Claude

Si tu reprends la conversation (ou en démarres une nouvelle) :

1. **Joins l'archive** `Dystos-projet-complet.zip` (ou au moins ce guide et le dossier du module concerné).
2. **Dis où tu en es** : par exemple « Étape 0 terminée, `mvn test` vert ; je démarre l'étape 2a ».
3. **Demande un livrable précis**, au format qui t'arrange : fichiers complets, avec leurs tests.

Modèle de message :
> Projet Dystos (guide `docs/GUIDE-PROJET.md` ci-joint). J'ai terminé l'étape X. Je veux faire l'étape Y : [objectif]. Voici l'archive actuelle / voici le message d'erreur : […].

Ne compte pas sur Claude pour se souvenir des détails : **ce guide et le code font foi**.

---

## 13. Aide-mémoire

```bash
mvn test                                     # toutes les suites (depuis la racine)
mvn -pl compiler/sema -am test               # un module (et ses dépendances)
scripts/playground --mode process            # service HTTP (voir --help)
scripts/generate-grammar.sh                  # régénérer les grammaires
scripts/test-editors.sh                      # vérifier les grammaires
scripts/ci.sh                                # tout, d'un coup
scripts/dystos build prog.dys -o prog.jar    # compiler vers la JVM (puis : java -jar prog.jar)
scripts/dystos jrun  prog.dys                # compiler et exécuter le bytecode
scripts/compare-backends.sh                  # interpréteur contre bytecode
scripts/bench.sh                             # mesures de performance
scripts/build.sh                             # compile sans Maven
scripts/dystos run  prog.dys                 # exécuter (mode développeur)
scripts/dystos run  prog.dys --sandbox       # exécuter avec limites strictes
scripts/dystos run  prog.dys --json          # résultat JSON
scripts/dystos test prog.dys                 # lancer les fonctions @test
scripts/run-examples.sh                      # les 20 exemples
```

### Documents historiques et ce qui a changé depuis
- `docs/01` (plan initial) : la **structure du dépôt** et la **liste des modules** ont évolué (`lexer`, `parser`, `sema`, `interp` ; l'interpréteur a été inséré avant le backend JVM). Les noms `pyva-*` y ont été remplacés par `dystos-*`. La section 2.1 utilise `self` explicite, conformément à la décision finale.
- `docs/02` (Phase 0) : à jour pour la syntaxe ; les décisions de typage (`/` flottant, pas de smart cast...) sont dans ce guide, §6.
- `docs/03` (site et application) : à jour ; l'interpréteur et le contrat de résultat d'exécution (§10) précisent l'API du playground.
