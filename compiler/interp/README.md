# dystos-interp (interpréteur d'AST)

Exécute un programme Dystos : lexer, parser, **analyse sémantique** (un programme qui ne passe pas
l'analyse n'est jamais exécuté), puis interprétation de l'arbre.

## Utilisation

```java
Runner.Result r = Runner.run(source, Runner.Options.sandbox());
System.out.print(r.stdout());
String json = r.toJson();   // {"stdout","stderr","exitCode","durationMs","diagnostics"}

Runner.TestReport t = Runner.runTests(source, Runner.Options.sandbox());   // fonctions @test
Runner.Result multi = Runner.runModules(Map.of("app.outils", a, "main", b), "main", opts);
```

Ligne de commande :

```
java -cp lexer.jar:parser.jar:sema.jar:interp.jar com.vulsoft.dystos.interp.Main run  programme.dys
java -cp ...                                         com.vulsoft.dystos.interp.Main test programme.dys
java -cp ...                                         com.vulsoft.dystos.interp.Main run  programme.dys --sandbox --json
```

## Codes de sortie

| Code | Signification |
|---|---|
| 0 | succès |
| 1 | exception non rattrapée (message et ligne sur stderr) |
| 2 | erreurs de compilation (voir `diagnostics`) ; rien n'est exécuté |
| 3 | limite dépassée : instructions, durée ou taille de sortie |
| 4 | erreur interne |

## Modes et limites

| | `Options.sandbox()` | `Options.trusted(dir)` |
|---|---|---|
| Instructions | 20 millions | pratiquement illimité |
| Sortie | 64 000 caractères | 50 millions |
| Profondeur d'appels | 800 | 2 000 |
| Durée | 5 s | 10 min |
| Fichiers (`io`) | désactivés | dans `dir` uniquement (`..` refusé) |
| Classes Java | `java.util`, `java.time`, `java.math`, `java.text`, quelques `java.lang` ; sont exclus `java.io`, `java.net`, `java.nio`, `Runtime`, `System`, `Thread`, `java.util.concurrent`... | tout `java.*` |

Les classes Java interdites sont refusées **dès l'analyse** (erreur `E0105`) et le pont d'exécution
revérifie la classe de chaque objet manipulé.

> **Sécurité.** Le mode sandbox borne les ressources mais n'est pas une isolation complète. Un service
> public (playground) doit en plus lancer chaque exécution dans un conteneur éphémère sans réseau, en
> lecture seule, avec limites de mémoire et de CPU. Le fil d'exécution est un fil « daemon » : un appel
> Java bloquant ne peut pas être interrompu de force.

## Représentation des valeurs

`int` = `Long` (débordement = erreur `RuntimeError`) · `float` = `Double` · `str` = `String` ·
`bool` = `Boolean` · `null` = `null` · `list` = `ArrayList` · `map` = `LinkedHashMap` (ordre d'insertion) ·
`set` = `LinkedHashSet`. Les objets Java manipulés par l'interopérabilité restent des objets Java.

`int` → `float` : la conversion est appliquée aux paramètres, retours, champs et variables **déclarés**
`float` (ou déjà flottants). Elle n'est pas appliquée à l'intérieur des collections (`[1, 2.5]` garde un
entier `1`) : cela viendra avec le backend JVM, qui connaîtra les types statiques.

## Affichage

`print(x, y)` sépare par un espace. Flottants à la Python (`13.0`, `0.1`, `1e+20`), booléens `true/false`,
`null`, listes `[1, "a"]`, maps `{"a": 1}`, ensembles `{1, 2}` (`set()` si vide). Objets : `toString(self)` s'il
existe, sinon `<Classe>`, constante d'énumération `Couleur.ROUGE`, exception = son message.
f-strings : `.2f`, `05d`, `>10`, `*^7`, `,`, `+d`, `x`, `b`, `e`, `.1%`.

## Particularités et limites actuelles

- `concurrent.run(...)` s'exécute **séquentiellement** (le vrai parallélisme viendra avec le backend JVM).
- Pas de lecture au clavier (`input`) ; pas de fonctions locales ni de tuples (voir la sémantique).
- Héritage d'une classe Java : non supporté à l'exécution.
- `/` donne toujours un flottant ; `%` suit le signe du diviseur (comme Python) ; `int ** int` est un `int` et un exposant négatif est une erreur (`2.0 ** -1` pour un flottant).
- Erreurs d'exécution (indice hors limites, clé absente, division par zéro...) : `RuntimeError` ; conversions
  invalides : `ValueError` ; toutes rattrapables par `except Exception`.
