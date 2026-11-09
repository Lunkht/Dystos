# dystos-sema (Phase 2)

Analyse sémantique de **Dystos** : résolution des noms, typage, null-safety, vérification des
classes et interfaces, interopérabilité Java. Dépend de `dystos-parser`.

## Utilisation

```java
SemanticAnalyzer.Result r = SemanticAnalyzer.check(source);        // un fichier
SemanticAnalyzer.Result r2 = SemanticAnalyzer.checkAll(Map.of(...)); // plusieurs modules (nom -> texte)
for (Diagnostic d : r.diagnostics()) System.out.println(d);
String json = r.toJson();                                           // pour le site et l'application
```

Les erreurs lexicales (`E0001`) et syntaxiques (`E0002`) deviennent aussi des diagnostics.

## Format JSON d'un diagnostic (contrat avec le playground)

```json
{"severity":"error","code":"E0201","message":"'null' ne peut pas être utilisé comme 'str' (utilisez 'str?')","line":6,"column":14}
```
`file` (nom du module) n'apparaît que lorsque plusieurs fichiers sont analysés.

## Codes

| Code | Sujet |
|---|---|
| E0001 / E0002 | erreur lexicale / syntaxique |
| E0101 | nom ou type inconnu (avec suggestion « vouliez-vous dire … ») |
| E0102 | nom déjà défini |
| E0103 | membre de module inconnu |
| E0104 | membre privé (préfixe `_`) hors de sa classe ou de son module |
| E0105 | import introuvable ou classe Java interdite |
| E0201 | null-safety (`null` ou type nullable là où un type non nul est requis, accès `.` sur nullable) |
| E0202 | types incompatibles |
| E0203 | opérateur non applicable |
| E0204 | condition qui n'est pas un `bool` |
| E0205 | arguments d'appel (nombre, noms, surcharge) |
| E0206 | valeur non appelable, classe ou module utilisé comme valeur |
| E0207 | type non déductible (annotation requise) |
| E0208 | `return` incorrect ou manquant |
| E0209 | membre inconnu |
| E0210 | valeur non itérable ou déstructuration impossible |
| E0211 | indexation ou tranche invalide |
| E0212 | affectation à une valeur immuable |
| E0213 | `raise` / `except` avec un type qui n'est pas une exception |
| E0214 | `with` sur un objet sans `close()` |
| E0215 | `break` / `continue` hors d'une boucle |
| E0301 | méthode abstraite non implémentée, corps manquant ou interdit |
| E0302 | redéfinition incompatible |
| E0303 | `self` manquant |
| E0304 | type de paramètre manquant |
| E0305 | héritage invalide ou cyclique |
| E0306 | décorateur inconnu |
| E0307 | point d'entrée ambigu (instructions de niveau fichier **et** `main`) |
| E0308 | instanciation impossible, `super()` mal placé |
| E0399 | fonctionnalité pas encore supportée (fonctions locales) |
| W0201 / W0202 | avertissements : `?.` ou `?:` inutiles |

## Règles de typage retenues (à valider)

- `int` se convertit en `float` ; l'inverse est refusé.
- `/` donne **toujours** un `float` (`4 / 2` vaut `2.0`) ; `int op int` donne un `int` pour `+ - * % **`.
- `+` n'assemble que des `str` entre eux : pas de conversion implicite (utiliser une f-string).
- Les conditions doivent être de type `bool` (pas de « vrai/faux » implicite).
- `x = 5` déclare `x` (type inféré) s'il n'existe pas, sinon réaffecte ; `val` et les paramètres sont immuables.
- Portée par bloc (comme Java), les champs ne sont accessibles que par `self.x`.
- Pas de surcharge pour les fonctions et méthodes Dystos (les classes Java gardent leurs surcharges).
- Une sous-classe sans `init` hérite du constructeur de sa superclasse.
- Pas encore de « smart cast » : `if x != null:` ne transforme pas `x` en type non nul.

## Interopérabilité Java

`import java.util.ArrayList`, `import java.util.*`, `ArrayList[str]()`, `LocalDate.now().year`.

- Correspondances : `String, char -> str` ; `byte..long -> int` ; `float, double -> float` ;
  `boolean -> bool` ; `void -> unit` ; `Object -> any`.
- Les accesseurs `getX()` / `isX()` sont aussi des propriétés `x`.
- Les types référence Java sont considérés non nuls.
- Seules les classes dont le nom commence par un préfixe autorisé (`java.`, `javax.` par défaut) sont
  chargeables, et elles le sont **sans exécuter leur initialisation statique**. Le paramètre
  `javaPrefixes` de `SemanticAnalyzer.analyze(...)` permet de le restreindre ; une liste vide désactive
  l'interopérabilité (recommandé pour le playground public).
- Pas encore de conversion `list` Dystos <-> `java.util.List` (prévue avec la bibliothèque standard).

## Limites actuelles

Fonctions locales, tuples, smart casts, surcharge de méthodes Dystos, `match` à motifs de déstructuration.
Les modules prédéfinis (`io`, `concurrent`) et les fonctions globales (`print`, `len`, `range`, `sum`,
`sorted`, `filter`, `enumerate`, `abs`, `min`, `max`) sont un embryon de bibliothèque standard.

## Fichiers

`SemanticAnalyzer` (API) · `Analyzer` (modules, imports, déclarations, classes) · `Checker` (corps, expressions) ·
`Types` / `Type` (types, compatibilité, unification) · `Symbols` / `Scope` · `Builtins` · `JavaInterop` ·
`Diagnostic` · `Suggest` (aide « vouliez-vous dire »).

## Programme typé : `TypedProgram`

`SemanticAnalyzer.Result.typed()` expose ce que les générateurs de code doivent savoir (tables indexées par **identité**
de nœud d'AST) :

| Table | Contenu |
|---|---|
| `types` | type statique de chaque expression |
| `names` | symbole désigné par chaque nom (variable, fonction, classe) |
| `calls` | pour chaque appel : nature (`CallKind`), fonction ou méthode choisie (surcharge résolue), classe instanciée, **types génériques inférés** |
| `members` | champ, propriété ou constante d'énumération désigné par chaque `x.nom` (lecture et cible d'affectation) |
| `declared` | variables déclarées par chaque instruction (`x = …`, `val`, `for`, compréhension, `except … as`, `with … as`) |
| `funcs` | `self` et paramètres de chaque fonction |
| `exceptTypes` | type attrapé par chaque clause `except` |
| `modules`, `units` | modules analysés |

Le backend JVM (`compiler/jvm`) est son premier consommateur.

Règle de typage ajoutée avec le backend : `int ** int` est un `int` et un **exposant négatif est une erreur** d'exécution
(`RuntimeError`) ; utilisez un flottant (`2.0 ** -1`).

## Interopérabilité Java

Les classes Java (`import java.util.ArrayList`) sont chargées par réflexion (`JavaInterop`) : constructeurs, méthodes, accesseurs
(`getX()` devient la propriété `x`) et champs publics. Les types sont traduits de façon volontairement simple (tous les entiers
en `int`, `float`/`double` en `float`, `char` et `String` en `str`, tableaux et `Object` en `any`).
Chaque symbole garde le membre Java réel (`FuncSymbol.javaMember`, `VarSymbol.javaMember`) pour que le backend JVM calcule le
descripteur exact. Quand plusieurs surcharges ont la même signature Dystos, la plus naturelle est conservée (`long` plutôt que
`int`, `double` plutôt que `float`, jamais un tableau) et les candidats sont classés du plus spécifique au plus général.
