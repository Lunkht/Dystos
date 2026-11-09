# dystos-jvm (Phase 3 : compilateur vers la JVM)

Compile un programme Dystos en **bytecode JVM** et produit un `.jar` autonome (exécutable avec `java -jar`).
Aucune dépendance : le fichier de classe est écrit par un écrivain maison (`classfile/`), sans ASM.
**Les 20 exemples du langage compilent**, et leur exécution donne la même sortie que l'interpréteur.

```
source .dys ─▶ lexer ─▶ parser ─▶ sema (TypedProgram) ─▶ MethodGen / ClassGen / ModuleGen / LambdaGen ─▶ .class ─▶ .jar
                                                              │                                          ▲
                                                              └── appelle dystos.rt.Rt ──────────────────┘ (runtime embarqué)
```

## Utilisation

```java
Compiler.Output o = Compiler.compile(source);          // ou compile(Map<module, texte>, entrée)
if (o.ok()) Compiler.writeJar(o, Path.of("programme.jar"));
else o.diagnostics().forEach(System.err::println);     // E0001/E0002, erreurs de sémantique, E0401…
```

```bash
scripts/dystos build prog.dys -o prog.jar && java -jar prog.jar
scripts/dystos jrun  prog.dys                          # compile dans un fichier temporaire puis exécute
scripts/compare-backends.sh                            # interpréteur contre bytecode sur tous les exemples
```

Codes de diagnostic propres au backend :
`E0401` — construction pas encore supportée (avec position) ; `E0402` — module d'entrée introuvable.
Un `E0401` n'est jamais accompagné de bytecode : **on ne génère pas de code incorrect**.

## Représentation à l'exécution

| Dystos | JVM |
|---|---|
| `int`, `float`, `bool` | `long` (J), `double` (D), `boolean` (Z) |
| `int?`, `float?`, `bool?` | `Long`, `Double`, `Boolean` (références) |
| `str` | `java.lang.String` |
| `list[T]`, `map[K,V]`, `set[T]` | `java.util.List/Map/Set` (`ArrayList`, `LinkedHashMap`, `LinkedHashSet`) ; éléments encadrés |
| génériques (`T`) | effacement en `Object` + `checkcast` aux lectures ; **`int → float` appliqué selon le type statique** |
| `(int) -> int` (fonction) | `dystos.rt.Fn` |
| `Exception`, `ValueError`, `RuntimeError` | `dystos.rt.DException`, `DValueError`, `DRuntimeError` |
| `File`, `Task` | `dystos.rt.DFile`, `dystos.rt.DTask` |
| classe, interface, énumération | classe, interface, classe dérivant de `dystos.rt.DEnum` |
| `self` | `this` (emplacement 0) |
| module `a.b` | classe `a/b/$Module` (fonctions statiques, `$run`, `main(String[])`) ; classes utilisateur dans `a/b/` |

Une classe sans superclasse dérive de `dystos.rt.DObject` (affichage par défaut `<Nom>`, comme l'interpréteur).
Les constructeurs suivent la règle Dystos : `init` est un constructeur ; `super().init(...)` doit être la **première
instruction** si le parent exige des arguments ; une sous-classe sans `init` reçoit des constructeurs qui redirigent
vers ceux de son ancêtre ; les initialiseurs de champs s'exécutent juste après l'appel au constructeur parent.

## Ce qui est supporté

Fonctions (arguments nommés, valeurs par défaut), scripts, modules multiples, `int`/`float`/`bool`/`str`, opérateurs
(dont `?.`, `?:`, `in`, `is`, courts-circuits), f-strings avec formats, listes/maps/ensembles et compréhensions,
indices négatifs et tranches, boucles (`for range` optimisé en boucle comptée), `if/elif/else`, `match`,
`try/except/finally` (le code de `finally` est dupliqué sur chaque sortie : `return`, `break`, `continue`, exception),
`raise`, `assert`, `with`, classes, héritage, interfaces (avec **méthodes par défaut et statiques**), classes abstraites,
`@static`, génériques de classes et de fonctions (**méthodes pont** pour les redéfinitions), énumérations, exceptions
personnalisées, **lambdas et fermetures**, **variables globales de script**, **modules `io` et `concurrent`**,
**interopérabilité Java** (voir plus bas), et les fonctions prédéfinies `print`, `len`, `range`, `sum`, `abs`, `min`, `max`,
`sorted` (avec `key=`), `filter`, `enumerate`, `int()`, `float()`, `str()`, `bool()`.

## Limites restantes : diagnostic `E0401`

- fonctions prédéfinies passées en valeur (`filter(len, …)`), appel d'un champ de type fonction (`self.callback(x)`) ;
- Java : tableaux, méthodes à nombre variable d'arguments (`String.format`), interfaces fonctionnelles (passer une
  lambda Dystos à du code Java), hériter d'une classe Java ou implémenter une interface Java, méthodes statiques
  d'interface Java (`List.of`, nécessitent des classes de version ≥ 52), affecter un champ Java ;
- champs d'instance dans une énumération, `super.methode()` dans une lambda ou une méthode par défaut ;
- fonctions locales (déclarées dans une autre fonction) ;
- méthode dépassant 64 Ko de bytecode ;
- tests `@test` : se lancent avec l'interpréteur (`scripts/dystos test`).

## Lambdas et fermetures

Une lambda devient une classe synthétique `<module>/$Lambda<n>` qui implémente `dystos.rt.Fn`
(`Object call(Object[])` : arguments et résultat toujours encadrés ; `null` pour une fonction sans résultat).
Pas besoin d'`invokedynamic` : la version de classe reste 49.

| Cas | Représentation |
|---|---|
| variable capturée jamais réaffectée (paramètre, `val`, variable de boucle, variable assignée une seule fois) | copiée dans un champ de la classe |
| variable capturée **et réaffectée** (`x = …`, `x += …`) | stockée dans une boîte `dystos.rt.Ref` partagée par la fonction et la lambda |
| variable globale de script | lue directement dans son champ statique (jamais capturée) |
| fonction de module passée en valeur | classe adaptatrice `$Lambda<n>` qui appelle la méthode statique |
| appel `f(x)` d'une valeur fonction | tableau d'arguments encadrés, `Fn.call`, résultat converti selon le type statique |

La sémantique (`TypedProgram.lambdas`, `capturedVars`, `reassigned`, `isBoxed`) calcule les captures ; le backend n'a qu'à les lire.

## Variables globales de script

Dans un script, une variable de niveau fichier lue ou modifiée par une fonction, une méthode ou une lambda devient un
**champ statique** de la classe `$Module` (`$g_<nom>`). Les autres restent des variables locales de `$run` (plus rapides).

## Méthodes pont et méthodes par défaut

- **Pont** : quand une redéfinition n'a pas le même descripteur JVM que la méthode parente (génériques effacés : `get(): T`
  redéfini en `get(): int`, ou type de retour plus précis), la classe reçoit une méthode au descripteur du parent qui convertit
  les arguments, appelle la redéfinition et convertit le résultat.
- **Méthodes par défaut d'interface** : le format 49 interdit un corps de méthode dans une interface. Le code vit dans une
  méthode statique de la classe compagnon `Interface$Defaults` ; chaque classe qui implémente l'interface et ne redéfinit pas
  la méthode reçoit une redirection (comme les `DefaultImpls` de Kotlin). Les méthodes statiques d'interface suivent le même schéma.

## Modules `io` et `concurrent`

- `io.open(chemin, mode)` : fichiers texte (`r`, `w`, `a`) **sous le répertoire courant** ; l'écriture est tamponnée et enregistrée
  à la fermeture (`with` ou `close()`). Mêmes règles et mêmes messages que l'interpréteur (classe `dystos.rt.DFile`).
- `concurrent.run(f)` : **vrai parallélisme** (un thread par tâche) ; `await()` attend et relance l'exception de la tâche.
  Une tâche jamais attendue va au bout avant la fin du programme. L'interpréteur, lui, exécute les tâches l'une après l'autre :
  l'**ordre des affichages** de tâches concurrentes n'est donc garanti qu'après `await`.

## Interopérabilité Java

`import java.util.ArrayList`, `ArrayList[str]()`, méthodes, propriétés (`d.year` pour `getYear()`), champs et constantes
statiques (`Integer.MAX_VALUE`, `Math.PI`), méthodes statiques (`Math.max`, `Integer.parseInt`). Le backend appelle directement
les classes Java (`invokevirtual`, `invokeinterface`, `invokestatic`, `new`) avec le **descripteur exact** de la méthode réelle
(`FuncSymbol.javaMember` rempli par la sémantique).

| De Dystos vers Java | Conversion |
|---|---|
| `int` vers `long` / `int` / `short` / `byte` | direct / `Rt.toJavaInt` (erreur si hors limites) |
| `float` vers `double` / `float` ; `int` vers `double` | direct / `d2f` ; `l2d` |
| `str` vers `char` | `Rt.toJavaChar` (chaîne de longueur 1) |
| primitif vers `Object`, `Number`, `Integer`… | encadrement (`Long.valueOf`, `Integer.valueOf`…) |
| `any` ou paramètre de type vers une classe précise | `checkcast` |

| De Java vers Dystos | Conversion |
|---|---|
| `int` / `short` / `byte` vers `int` ; `float` vers `float` | `i2l` ; `f2d` |
| `char` vers `str` | `String.valueOf` |
| `Integer`, `Long`, élément de collection vers `int` | `Rt.javaToLong` (erreur claire si `null`) |

Un résultat Java **nul utilisé là où un `int`, `float` ou `bool` est attendu** est une `RuntimeError` (« valeur Java nulle… »),
conformément à la null-safety du langage ; l'interpréteur, lui, laisse passer `null`. Un résultat simplement ignoré
(`m.put("a", 1)`) n'est pas converti. Plusieurs surcharges Java de même signature Dystos (`max(int,int)` / `max(long,long)`)
sont réduites à la plus naturelle par la sémantique, et classées du plus spécifique au plus général.

## Performances

Mesures indicatives (`scripts/bench.sh`, une exécution par programme sur une seule machine, avec une variation de
l'ordre de 20 % d'une exécution à l'autre ; le temps du bytecode inclut
le démarrage de la JVM, environ 0,04 s) :

| Charge (`benchmarks/`) | Interpréteur | Bytecode | Gain |
|---|---|---|---|
| `fib` : `fib(35)` récursif | 7 à 9 s | 0,1 s | ≈ 75 à 95× |
| `boucle` : 30 millions d'itérations avec `%` et `+=` | 5,6 à 5,9 s | 0,11 s | ≈ 50× |
| `objets` : 4 millions d'objets créés et appelés | 3,3 à 3,6 s | 0,1 s | ≈ 30 à 40× |
| `listes` : 1 million d'éléments encadrés, tri, `map` | 1,7 s | 0,65 à 0,7 s | ≈ 2,5× |

Le gain est considérable sur le calcul (boucles, appels, objets) et modeste sur les programmes courts qui allouent
beaucoup d'entiers encadrés : l'échauffement de la JVM et l'allocation dominent. Pistes : listes de primitives sans
encadrement. Un chemin rapide du tri (ordre naturel) a été essayé puis retiré : aucun gain mesurable.

## Différences connues avec l'interpréteur

| Cas | Interpréteur | Bytecode |
|---|---|---|
| `int → float` dans une collection ou un paramètre de type (`[1, 2.5]` annoté `list[float]`, `Boite[float](1)`) | affiche `1` | affiche `1.0` (**le bytecode a raison**) |
| résultat Java nul là où un `int` est attendu | `null` | `RuntimeError` (null-safety) |
| `range(n)` très grand | l'interpréteur construit la liste et refuse au-delà d'un plafond (« range() trop grand ») | `for i in range(n)` est une boucle comptée sans liste : aucune limite |
| récursion infinie | `RuntimeError` rattrapable | `StackOverflowError` : arrêt avec `Erreur non rattrapée … RuntimeError`, code 1 |
| tâches `concurrent` | exécutées l'une après l'autre | en parallèle |
| limites (instructions, durée, sortie) | oui (sandbox) | aucune : un programme compilé est un programme Java ordinaire |

Les numéros de ligne des erreurs non rattrapées sont identiques (table de lignes `LineNumberTable`).

## Objets non initialisés et boucles

Le vérificateur des classes de version 49 interdit un saut en arrière tant qu'un objet non initialisé est sur la pile.
Quand les arguments d'un constructeur (ou d'un `super().init(...)`) contiennent une compréhension, ils sont donc évalués
**avant** le `new`, dans des variables temporaires.

## Format de classe : version 49

Le générateur écrit des fichiers de classe de **version 49** (Java 5). Avantage : pas de `StackMapTable` à calculer, la JVM vérifie
le bytecode par inférence (un test vérifie qu'un bytecode invalide est bien refusé avec `VerifyError`). Contreparties : pas
d'`invokedynamic`, pas de méthode d'interface avec corps, pas d'appel statique d'interface Java. Les contournements ci-dessus
(classes de lambdas, classe compagnon `$Defaults`) rendent cela invisible pour le programmeur Dystos. Passer à une version ≥ 52
demandera de calculer les cartes de pile (ou d'adopter ASM) ; ce n'est plus nécessaire pour les fonctionnalités du langage.

## Architecture du module

| Classe | Rôle |
|---|---|
| `Compiler` | API : analyse, génération, écriture du `.jar` (runtime embarqué) |
| `ModuleGen` | classe `$Module` d'un module, `$run`, `main(String[])`, variables globales de script |
| `ClassGen` | classes, interfaces (et classe compagnon), énumérations, constructeurs, ponts, redirections |
| `MethodGen` | instructions et expressions ; coercions ; `try/finally` ; `match` ; appels (Dystos, Java, valeurs fonction) |
| `LambdaGen` | classes des lambdas et adaptateurs de fonctions nommées |
| `JavaCall` | descripteurs Java exacts et conversions aux frontières |
| `Jt` | types Dystos → descripteurs et noms JVM ; substitution des paramètres de type |
| `RuntimeMap` | fonctions et méthodes prédéfinies → méthodes de `dystos.rt.Rt` (**vérifie à la compilation qu'elles existent**) |
| `AstWalk` | parcours générique de l'AST |
| `classfile/` | `ClassWriter`, `Code` (pile, étiquettes, exceptions, lignes), `ConstPool`, `Label` |
| `BuildMain` | lanceur `build` / `run` |

Principe de `MethodGen` : après `expr(e)`, la pile contient la valeur du **type statique** de `e` (fourni par `TypedProgram`).
`coerce(from, to)` convertit entre `int`/`float`/encadrés/génériques effacés ; chaque appel convertit le type déclaré (effacé)
du résultat vers le type statique de l'expression.

## Tests

`JvmTest` : **tests différentiels**. Chaque programme est exécuté par l'interpréteur et, compilé, par une vraie JVM
(`java -jar`, dans un répertoire de travail propre) ; sortie standard et code de sortie doivent être identiques (et, pour les
erreurs non rattrapées, la sortie d'erreur avec son numéro de ligne). `ClassWriterTest` charge des classes générées à la main.

**Ajouter une fonctionnalité** : (1) si besoin, enregistrer l'information dans `TypedProgram` côté sémantique ; (2) générer
le bytecode dans `MethodGen`/`ClassGen` ; (3) ajouter une méthode à `Rt` si un appel est nécessaire (et sa correspondance dans
`RuntimeMap`) ; (4) écrire un test `same(...)` ; (5) retirer la construction de la liste des limites.
