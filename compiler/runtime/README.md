# dystos-runtime (bibliothèque d'exécution des programmes compilés)

Package `dystos.rt`. Les programmes compilés vers la JVM appellent ces classes ; `Compiler.writeJar` les **copie dans le
`.jar` généré** : un programme Dystos compilé est autonome (il faut seulement une JVM 17 ou plus).
Aucune dépendance, compilé en **Java 17**.

| Classe | Rôle |
|---|---|
| `Rt` | méthodes statiques appelées par le code généré : affichage, arithmétique avec détection de débordement, comparaisons, indices et tranches, méthodes de `str`/`list`/`map`/`set`, fonctions prédéfinies, conversions, arrêt sur exception non rattrapée |
| `Fmt` | formats d'f-string (`.2f`, `05d`, `>10`, `,`, `+d`, `x`, `.1%`…) |
| `DObject` | base des objets Dystos (affichage `<Nom>`) |
| `DException`, `DValueError`, `DRuntimeError` | exceptions Dystos ; champ public `message` |
| `DEnum` | base des énumérations (`name`, `ordinal`) |
| `Entry` | paire clé/valeur (`map.items()`, `enumerate`) |
| `DFile` | fichier texte (`io.open`) : modes `r`/`w`/`a`, écriture tamponnée, confiné au répertoire courant |
| `DTask` | tâche concurrente (`concurrent.run`) : un thread par tâche, `await()` relance l'exception |
| `Fn` | fonction comme valeur : `Object call(Object[])`, arguments et résultat encadrés |
| `Ref` | boîte partagée pour une variable capturée par une lambda et réaffectée |

**Contrat avec le compilateur** : le nom et le descripteur de chaque méthode publique de `Rt` utilisée par le code généré font
partie de l'interface (`RuntimeMap` du module `jvm` les vérifie à la compilation). Ne renommez pas une méthode sans mettre
`RuntimeMap` / `MethodGen` à jour.

**Parité avec l'interpréteur** : mêmes règles d'affichage, de formats, d'indices négatifs, mêmes messages d'erreur. Les deux
implémentations existent séparément : toute règle changée doit l'être des deux côtés ; les tests différentiels du module `jvm`
détectent les écarts.
